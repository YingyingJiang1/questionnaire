    private Trun parseTrun() throws IOException {
        final Trun obj = new Trun();
        obj.bFlags = stream.readInt();
        obj.entryCount = stream.readInt(); // unsigned int

        obj.entriesRowSize = 0;
        if (hasFlag(obj.bFlags, 0x0100)) {
            obj.entriesRowSize += 4;
        }
        if (hasFlag(obj.bFlags, 0x0200)) {
            obj.entriesRowSize += 4;
        }
        if (hasFlag(obj.bFlags, 0x0400)) {
            obj.entriesRowSize += 4;
        }
        if (hasFlag(obj.bFlags, 0x0800)) {
            obj.entriesRowSize += 4;
        }
        obj.bEntries = new byte[obj.entriesRowSize * obj.entryCount];

        if (hasFlag(obj.bFlags, 0x0001)) {
            obj.dataOffset = stream.readInt();
        }
        if (hasFlag(obj.bFlags, 0x0004)) {
            obj.bFirstSampleFlags = stream.readInt();
        }

        stream.read(obj.bEntries);

        for (int i = 0; i < obj.entryCount; i++) {
            final TrunEntry entry = obj.getEntry(i);
            if (hasFlag(obj.bFlags, 0x0100)) {
                obj.chunkDuration += entry.sampleDuration;
            }
            if (hasFlag(obj.bFlags, 0x0200)) {
                obj.chunkSize += entry.sampleSize;
            }
            if (hasFlag(obj.bFlags, 0x0800)) {
                if (!hasFlag(obj.bFlags, 0x0100)) {
                    obj.chunkDuration += entry.sampleCompositionTimeOffset;
                }
            }
        }

        return obj;
    }
