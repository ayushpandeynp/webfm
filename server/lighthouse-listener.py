# Author: Ayush Pandey (ayush.pandey@nyu.edu)
# This script listens for new URL requests in the database and runs Lighthouse audits on them. Measurements are saved in the lighthouse-reports directory.

import sqlite3
import subprocess
import time
from datetime import datetime
import os

DB_FILE = "sonic.db"
LIGHTHOUSE_REPORT_DIR = "./lighthouse-reports"

os.makedirs(LIGHTHOUSE_REPORT_DIR, exist_ok=True)

def init_db():
    """Initializes the lh_requests table if it doesn't exist."""
    with sqlite3.connect(DB_FILE, check_same_thread=False) as conn:
        cursor = conn.cursor()
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS lh_requests (
                request_id INTEGER PRIMARY KEY,
                url TEXT,
                processed_at TIMESTAMP
            )
        ''')
        conn.commit()


def fetch_pending_requests():
    """Fetches all requests with type='url' that have not been processed."""
    with sqlite3.connect(DB_FILE) as conn:
        cursor = conn.cursor()
        cursor.execute('''
            SELECT id, data FROM requests
            WHERE type = 'url'
            AND id NOT IN (SELECT request_id FROM lh_requests)
        ''')
        return cursor.fetchall()


def run_lighthouse(url):
    """Runs Lighthouse for the given URL and saves the report."""
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    report_file = os.path.join(LIGHTHOUSE_REPORT_DIR, f"{url.replace(':', '').replace('/', '_')}_{timestamp}.json")
    try:
        print(f"Running Lighthouse for: {url}")
        subprocess.run(
            [
                "lighthouse", url,
                "--output", "json",
                "--output-path", report_file,
                "--chrome-flags=--headless"
            ],
            check=True
        )
        print(f"Lighthouse report saved at: {report_file}")
    except subprocess.CalledProcessError as e:
        print(f"Error running Lighthouse for {url}: {e}")
        # report_file = None
    return report_file


def mark_as_processed(request_id, url):
    """Marks a request as processed by adding it to the lh_requests table."""
    with sqlite3.connect(DB_FILE) as conn:
        cursor = conn.cursor()
        cursor.execute('''
            INSERT OR IGNORE INTO lh_requests (request_id, url, processed_at)
            VALUES (?, ?, ?)
        ''', (request_id, url, datetime.now()))
        conn.commit()


def main():
    init_db()
    print("Monitoring database for new URLs...")

    while True:
        pending_requests = fetch_pending_requests()
        for request_id, url in pending_requests:
            report_file = run_lighthouse(url)
            if report_file:
                mark_as_processed(request_id, url)

        time.sleep(10)


if __name__ == "__main__":
    main()
