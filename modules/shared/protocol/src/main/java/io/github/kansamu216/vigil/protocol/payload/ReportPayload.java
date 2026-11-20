package io.github.kansamu216.vigil.protocol.payload;

import io.github.kansamu216.vigil.model.ClientReport;
import io.github.kansamu216.vigil.model.ModEntry;
import io.github.kansamu216.vigil.model.Observation;
import io.github.kansamu216.vigil.protocol.ProtocolException;
import io.github.kansamu216.vigil.protocol.ProtocolLimits;
import io.github.kansamu216.vigil.protocol.WireReader;
import io.github.kansamu216.vigil.protocol.WireWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户端报告编解码。
 *
 * <p>两个列表都先读数量再按数量读元素，数量超上限直接拒绝，绝不据此预分配大数组。</p>
 */
public final class ReportPayload {
    private static final Observation TRUNCATED = new Observation("COLLECT_REPORT_TRUNCATED", "", 0);

    private ReportPayload() {
    }

    public static byte[] encode(ClientReport report) {
        WireWriter writer = writeHeader(report, ProtocolLimits.MAX_PAYLOAD_BYTES);
        return writeEntries(writer, report.mods(), report.observations());
    }

    public static byte[] encodeFitting(ClientReport report, int budget) {
        WireWriter writer = writeHeader(report, budget);
        int remaining = budget - writer.size() - 2 * Short.BYTES;
        int completeSize = report.mods().stream().mapToInt(ReportPayload::modSize).sum()
                + report.observations().stream().mapToInt(ReportPayload::observationSize).sum();
        if (completeSize <= remaining) {
            return writeEntries(writer, report.mods(), report.observations());
        }
        remaining -= observationSize(TRUNCATED);
        List<Observation> observations = new ArrayList<Observation>();
        List<ModEntry> mods = new ArrayList<ModEntry>();
        for (Observation observation : report.observations()) {
            int size = observationSize(observation);
            if (observations.size() >= ProtocolLimits.MAX_OBSERVATIONS - 1 || size > remaining) {
                break;
            }
            observations.add(observation);
            remaining -= size;
        }
        for (ModEntry mod : report.mods()) {
            int size = modSize(mod);
            if (size > remaining) {
                break;
            }
            mods.add(mod);
            remaining -= size;
        }
        if (mods.size() != report.mods().size() || observations.size() != report.observations().size()) {
            observations.add(TRUNCATED);
        }
        return writeEntries(writer, mods, observations);
    }

    private static int stringSize(String value) {
        int length = value.getBytes(StandardCharsets.UTF_8).length;
        return length <= ProtocolLimits.MAX_STRING_BYTES
                ? Short.BYTES + length : ProtocolLimits.MAX_PAYLOAD_BYTES + 1;
    }

    private static int modSize(ModEntry mod) {
        return stringSize(mod.id()) + stringSize(mod.version()) + stringSize(mod.fileName())
                + Long.BYTES + stringSize(mod.sha256());
    }

    private static int observationSize(Observation observation) {
        return stringSize(observation.code()) + stringSize(observation.detail()) + Byte.BYTES;
    }

    private static WireWriter writeHeader(ClientReport report, int budget) {
        WireWriter writer = new WireWriter(1024, budget);
        writer.u16(report.protocolVersion());
        writer.string(report.nonce());
        writer.string(report.playerId());
        writer.string(report.playerName());
        writer.string(report.fingerprintHash());
        writer.i64(report.collectedAtMillis());
        return writer;
    }

    private static byte[] writeEntries(WireWriter writer, List<ModEntry> mods, List<Observation> observations) {
        writer.u16(mods.size());
        for (ModEntry mod : mods) {
            writer.string(mod.id());
            writer.string(mod.version());
            writer.string(mod.fileName());
            writer.i64(mod.sizeBytes());
            writer.string(mod.sha256());
        }

        writer.u16(observations.size());
        for (Observation observation : observations) {
            writer.string(observation.code());
            writer.string(observation.detail());
            writer.u8(observation.severity());
        }
        return writer.toByteArray();
    }

    public static ClientReport decode(byte[] payload) throws ProtocolException {
        WireReader reader = new WireReader(payload);
        int protocolVersion = reader.u16();
        if (protocolVersion <= 0 || protocolVersion > 0xFFFF) {
            throw new ProtocolException(
                    ProtocolException.Code.FIELD_OUT_OF_RANGE, "report protocol version");
        }
        String nonce = reader.string();
        String playerId = reader.string();
        String playerName = reader.string();
        String fingerprint = reader.string();
        long collectedAt = reader.i64();

        int modCount = reader.count(ProtocolLimits.MAX_MOD_ENTRIES, "mods");
        List<ModEntry> mods = new ArrayList<ModEntry>(Math.min(modCount, 64));
        try {
            for (int i = 0; i < modCount; i++) {
                mods.add(new ModEntry(
                        reader.string(), reader.string(), reader.string(), reader.i64(), reader.string()));
            }
            int observationCount = reader.count(ProtocolLimits.MAX_OBSERVATIONS, "observations");
            List<Observation> observations = new ArrayList<Observation>(Math.min(observationCount, 32));
            for (int i = 0; i < observationCount; i++) {
                observations.add(new Observation(reader.string(), reader.string(), reader.u8()));
            }
            if (reader.remaining() != 0) {
                throw new ProtocolException(
                        ProtocolException.Code.LENGTH_MISMATCH, "trailing bytes in report payload");
            }
            return new ClientReport(
                    protocolVersion,
                    nonce,
                    playerId,
                    playerName,
                    mods,
                    observations,
                    fingerprint,
                    collectedAt);
        } catch (IllegalArgumentException e) {
            throw new ProtocolException(ProtocolException.Code.FIELD_OUT_OF_RANGE, e.getMessage());
        }
    }
}
