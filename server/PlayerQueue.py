# Author: Ayush Pandey (ayush.pandey@nyu.edu)
# This class manages the queue of the .wav files that are to be played.

import os
import pygame
import random
from time import sleep
from config import *
from utils import is_time_between
from logger import setupLogger
import sqlite3

class PlayerQueue:
    def __init__(self):
        self.logger = setupLogger("PlayerQueue")
        self.queue = []
        self.reQueue = []
        self.idleQueue = []
        
        self.isIdle = False

        os.makedirs("sent_wav", exist_ok=True)

        pygame.mixer.init()

        self.db = sqlite3.connect("sonic.db", check_same_thread=False)
        items = self.db.execute("SELECT filename FROM requests WHERE completed_status = 1 AND (type = 'url' or type = 'gpt') ORDER BY timestamp DESC").fetchall()
        for row in items:
            filename = row[0]
            self.add("data/" + filename + ".wav")

        print("Total unplayed items", len(items))


    def add(self, path: str):
        self.queue.append(path)

        self.reQueue.append(path)

        random.shuffle(self.reQueue)

    def addIdle(self, path: str):
        self.idleQueue.append(path)

    def play(self):
        retransmit = False
        qC = 0
        rC = 0

        while True:
            if is_time_between(BROADCAST_START_HOUR, BROADCAST_START_MINUTE + 15, BROADCAST_END_HOUR, 0):
                if self.queue and not retransmit:
                    self.isIdle = False
                    current = self.queue.pop(0)
                    
                    self.logger.info(f"Playing {current}")
                    try:
                        self.playsound(current)
                    except:
                        pass

                    filename = current.split("/")[1].split(".")[0]
                    self.db.execute(
                            "UPDATE requests SET completed_status = ? WHERE filename = ?", (2, filename))
                    self.db.commit()

                    qC += 1
                    if qC % 10 == 0:
                        rC = 0
                        retransmit = True

                elif self.reQueue:
                        self.isIdle = False
                        current: str = self.reQueue.pop(0)
                        self.logger.info(f"Replaying {current}")

                        try:
                            self.playsound(current)
                        except:
                            pass

                        rC += 1
                        if rC % 5 == 0:
                            retransmit = False
                else:
                    retransmit = False
                    self.isIdle = True
            else:
                retransmit = False
                self.isIdle = True

            if self.isIdle:
                if self.idleQueue and is_time_between(BROADCAST_START_HOUR, BROADCAST_START_MINUTE + 15, BROADCAST_END_HOUR, 0):
                    current = self.idleQueue.pop(0)
                    self.logger.info(f"Playing idle {current}")
                    self.isIdle = False

                    try:
                        self.playsound(current)
                    except:
                        pass
                else:
                    self.playsound("c137.wav")

    def playsound(self, src: str):
        pygame.mixer.music.load(src)
        pygame.mixer.music.play()

        # wait until the sound finishes playing
        while pygame.mixer.music.get_busy():
            sleep(0.1)