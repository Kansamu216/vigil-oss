package io.github.kansamu216.vigil.protocol.payload;

import io.github.kansamu216.vigil.model.ClientReport;
import io.github.kansamu216.vigil.model.ModEntry;
import io.github.kansamu216.vigil.model.Observation;
import io.github.kansamu216.vigil.protocol.ProtocolException;
import io.github.kansamu216.vigil.protocol.ProtocolLimits;
import io.github.kansamu216.vigil.protocol.WireReader;
import io.github.kansamu216.vigil.protocol.WireWriter;
import java.util.ArrayList;
import java.util.List;

public final class ReportPayload {

    private ReportPayload() {
    }

    public static byte[] encode(ClientReport report) {
        WireWriter writer = new WireWriter(1024, ProtocolLimits.MAX_PAYLOAD_BYTES);
        writer.u16(report.protocolVersion());
        writer.string(report.nonce());
        writer.string(report.playerId());
        writer.string(report.playerName());
        writer.string(report.fingerprintHash());
        writer.i64(report.collectedAtMillis());

        writer.u16(report.mods().size());
        for (ModEntry mod : report.mods()) {
            writer.string(mod.id());
            writer.string(mod.version());
            writer.string(mod.fileName());
            writer.i64(mod.sizeBytes());
            writer.string(mod.sha256());
        }

        writer.u16(report.observations().size());
        for (Observation observation : report.observations()) {
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
        for (int i = 0; i < modCount; i++) {
            mods.add(new ModEntry(
                    reader.string(), reader.string(), reader.string(), reader.i64(), reader.string()));
        }

        int observationCount = reader.count(ProtocolLimits.MAX_OBSERVATIONS, "observations");
        List<Observation> observations =
                new ArrayList<Observation>(Math.min(observationCount, 32));
        for (int i = 0; i < observationCount; i++) {
            observations.add(new Observation(reader.string(), reader.string(), reader.u8()));
        }

        if (reader.remaining() != 0) {
            throw new ProtocolException(
                    ProtocolException.Code.LENGTH_MISMATCH, "trailing bytes in report payload");
        }
        try {
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
