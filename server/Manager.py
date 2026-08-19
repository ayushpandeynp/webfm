# Author: Ayush Pandey (ayush.pandey@nyu.edu)

# This is the SONIC Server Manager.
# It will manage the ScreenshotQueue and the transmission of the data via FM.

from ScreenshotQueue import ScreenshotQueue
from PlayerQueue import PlayerQueue
from SMS import SMS
from SonicEncoder import SonicEncoder
import threading
import docker
import os
import time
from logger import setupLogger
from utils import *
from Metrics import Metrics
import sqlite3
import pandas as pd
from config import REQUEST_METHOD, ENCODING_PROFILE, IDLE_PAGES_LIST

class Manager:
    def __init__(self) -> None:
        self.logger = setupLogger("Manager")
        
        self.profile = ENCODING_PROFILE
        
        self.receiveMethod = REQUEST_METHOD
        
        os.makedirs("data", exist_ok=True)

        self.db = sqlite3.connect("sonic.db", check_same_thread=False)
        self.db.execute(
            "CREATE TABLE IF NOT EXISTS idlePagesTime (id INTEGER PRIMARY KEY, timestamp INTEGER)"
        )
        self.db.execute(
            "CREATE TABLE IF NOT EXISTS requests (id INTEGER PRIMARY KEY, sender TEXT, type TEXT, data TEXT, filename TEXT, completed_status INTEGER, timestamp INTEGER)")
        self.db.commit()

        self.encoder = SonicEncoder()

        # initialize the docker container with image ubuntu_quiet:2024
        self.client = docker.from_env()

        if self.receiveMethod == "sms":
            self.smsManager = SMS(self.newURL, self.newQuestion, self.newHeartbeat, self.newAck)
        
        self.screenshotQueue = ScreenshotQueue(self)
        
        self.playerQueue = PlayerQueue()

        self.transmitQueue = []

        self.metricsManager = Metrics()

        self.idlePages = []
        self.populateIdlePages()

    def populateIdlePages(self):
        idlePages = pd.read_csv(IDLE_PAGES_LIST, header=None).iloc[:, 0].tolist()
        self.idlePages = list(set(idlePages + self.idlePages))
        self.db.execute("INSERT INTO idlePagesTime (timestamp) VALUES (?)", (int(time.time()),))
        self.db.commit()

    def manage(self):
        # delete all existing containers with image ayush/ubuntu_quiet:2024
        for container in self.client.containers.list():
            if container.attrs['Config']['Image'] == 'ayush/ubuntu_quiet:2024':
                container.remove(force=True)

        self.container = self.client.containers.run(
            'ayush/ubuntu_quiet:2024',
            '/bin/bash',
            detach=True,
            tty=True,
            stdin_open=True
        )
        self.logger.info("Docker container started")

        self.screenshotThread = threading.Thread(
            target=self.screenshotQueue.processScreenshots)
        self.screenshotThread.start()
        self.logger.info("Screenshot thread started")

        self.transmitThread = threading.Thread(target=self.transmitData)
        self.transmitThread.start()
        self.logger.info("Transmit thread started")

        self.playerThread = threading.Thread(target=self.playerQueue.play)
        self.playerThread.start()
        self.logger.info("Player thread started")

        self.metricsThread = threading.Thread(target=self.metricsManager.collect)
        self.metricsThread.start()
        self.logger.info("Metrics thread started")

        self.idleThread = threading.Thread(target=self.manageIdleScreenshots)
        self.idleThread.start()
        self.logger.info("Idle thread started")

        # failsafe for transmitData method
        result = self.db.execute("SELECT * FROM requests WHERE completed_status = 0").fetchall()
        for row in result:
            url = row[3]
            if row[2] == "url":
                filename, cached = self.screenshotQueue.put(url, False)
                
                self.transmitQueue.append({
                    "type": "img",
                    "filename": filename,
                    "url": url
                })

            elif row[2] == "gpt":
                self.transmitQueue.append({
                    "type": "gpt",
                    "filename": randomFileName(10),
                    "url": url
                })

        if self.receiveMethod == "sms":
            self.smsManager.listen()

    def newURL(self, sender: str, url: str) -> None:
        filename, cached = self.screenshotQueue.put(url, False)

        if not any(item["url"] == url for item in self.transmitQueue):
            result = self.db.execute(
                "SELECT * FROM requests WHERE data = ? ORDER BY timestamp DESC LIMIT 1", (url,)).fetchone()
            if not result or result[4] != 1:
                self.transmitQueue.append({
                    "type": "img",
                    "filename": filename,
                    "url": url
                })

                print(url, "added to transmit queue")

        self.db.execute(
                "INSERT INTO requests (sender, type, data, filename, completed_status, timestamp) VALUES (?, ?, ?, ?, ?, ?)", (sender, "url", url, filename, 0, int(time.time())))
        self.db.commit()

    def newIdleUrl(self, url: str) -> None:
        filename, cached = self.screenshotQueue.put(url, True)
        if not any(item["url"] == url for item in self.transmitQueue):
            self.transmitQueue.append({
                "type": "img",
                "filename": filename,
                "url": url,
                "idle": True
            })

            print(url, "IDLE added to transmit queue")

    def newQuestion(self, sender: str, question: str) -> str:
        filename = randomFileName(10)
        self.transmitQueue.append({
            "type": "gpt",
            "filename": filename,
            "url": question
        })

        self.db.execute(
            "INSERT INTO requests (sender, type, data, filename, completed_status, timestamp) VALUES (?, ?, ?, ?, ?, ?)", (sender, "gpt", question, filename, 0, int(time.time())))
        self.db.commit()

    def newHeartbeat(self, sender: str, data: str) -> None:
        self.db.execute(
            "INSERT INTO requests (sender, type, data, filename, completed_status, timestamp) VALUES (?, ?, ?, ?, ?, ?)", (sender, "heartbeat", data, "", 1, int(time.time())))
        self.db.commit()
        
    def newAck(self, sender: str, data: str) -> None:
        self.db.execute(
            "INSERT INTO requests (sender, type, data, filename, completed_status, timestamp) VALUES (?, ?, ?, ?, ?, ?)", (sender, "ack", data, "", 1, int(time.time())))
        self.db.commit()

    def manageIdleScreenshots(self) -> None:
        while True:
            if self.playerQueue.isIdle:
                lastIdleTime = self.db.execute("SELECT timestamp FROM idlePagesTime ORDER BY id DESC LIMIT 1").fetchone()[0]
                if time.time() - lastIdleTime > 8 * 60 * 60:
                    self.populateIdlePages()

                if self.idlePages:
                    self.newIdleUrl(self.idlePages.pop(0))

    def transmitData(self) -> None:
        while True:
            if self.transmitQueue:
                for i in range(len(self.transmitQueue) - 1, -1, -1):
                    sonic_filename = self.transmitQueue[i]["filename"]

                    url = self.transmitQueue[i]["url"]
                    t = self.transmitQueue[i]["type"]
                    isIdle = self.transmitQueue[i].get("idle", False)

                    screenshotFile = f"screenshots/{sonic_filename}.png"
                    sonic_file = f"data/{sonic_filename}.sonic"

                    if t == "gpt":
                        try:
                            self.encoder.encodeGPT(sonic_file, url)
                            print("GPT response encoded")
                        except Exception as e:
                            print(e)
                            print("retrying for ", sonic_filename)

                    if os.path.exists(screenshotFile):
                        print("Screenshot exists", screenshotFile)
                        # wait for the screenshot to be saved properly
                        time.sleep(2)
                        try:
                            self.encoder.encode(screenshotFile, sonic_file, url)
                        except Exception as e:
                            print(e)
                            print("retrying for ", sonic_filename)

                    if os.path.exists(sonic_file):
                        print("Sonic exists", sonic_file)
                        os.system(
                            f"docker cp data/{sonic_filename}.sonic {self.container.id}:/home/ubuntu/")
                        self.container.exec_run(
                            f"./encoder {self.profile} ./{sonic_filename}.sonic", workdir="/home/ubuntu")
                        os.system(
                            f"docker cp {self.container.id}:/home/ubuntu/encoded.wav data/{sonic_filename}.wav")
                        self.container.exec_run(
                            f"rm /home/ubuntu/encoded.wav && rm /home/ubuntu/{sonic_filename}.sonic", workdir="/home/ubuntu")

                        if isIdle:
                            print(sonic_filename, "added to idle queue")
                            self.playerQueue.addIdle(f"data/{sonic_filename}.wav")
                        else:
                            print(sonic_filename, "added to player queue")
                            self.playerQueue.add(f"data/{sonic_filename}.wav")

                        self.logger.info(f"New item on transmission queue {sonic_filename}.sonic")
                        self.transmitQueue.pop(i)

                        self.db.execute(
                            "UPDATE requests SET completed_status = ? WHERE data = ?", (1, url))
                        self.db.commit()
