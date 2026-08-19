# Author: Ayush Pandey (ayush.pandey@nyu.edu)
# This class handles caching of URLs and their corresponding filenames within the transmission window.

import sqlite3
import time

class Cache:
    def __init__(self) -> None:
        self.conn = sqlite3.connect("cache.db", check_same_thread=False)
        self.conn.execute(
            "CREATE TABLE IF NOT EXISTS cache (url TEXT, filename TEXT, timestamp INTEGER)")
        self.conn.commit()

    def getFilename(self, url: str):
        cursor = self.conn.cursor()
        cursor.execute("SELECT timestamp, filename FROM cache WHERE url = ? ORDER BY timestamp DESC LIMIT 1", (url,))
        row = cursor.fetchone()

        if row:
            date, filename = row
            now = time.time()
            diffrence_in_hours = (now - date) / 3600

            if diffrence_in_hours < 7: # within 7 hours
                return filename
            
        return None
    
    def put(self, url: str, filename: str):
        self.conn.execute("INSERT INTO cache (url, filename, timestamp) VALUES (?, ?, ?)", (url, filename, time.time()))
        self.conn.commit()