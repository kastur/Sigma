package edu.ucla.nesl.sigma.samples.chat;

import java.util.HashMap;

public class PictureDB {

  final HashMap<String, PictureEntry> mEntries;

  public PictureDB() {
    mEntries = new HashMap<String, PictureEntry>();
  }

  public synchronized void addEntry(PictureEntry entry) {
    mEntries.put(entry.uuid, entry);
  }

  public synchronized PictureEntry getEntry(String uuid) {
    if (!mEntries.containsKey(uuid)) {
      return null;
    }
    return mEntries.get(uuid);
  }
}
