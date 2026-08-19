# Author: Ayush Pandey (ayush.pandey@nyu.edu)
# This class handles SMS communication with the dongle.

from huawei_lte_api.Client import Client
from huawei_lte_api.AuthorizedConnection import AuthorizedConnection

import time
from logger import setupLogger
import sqlite3
import re
import os
import dotenv
dotenv.load_dotenv()

class SMS:
    def __init__(self, newURLCallback: callable, newQuestionCallback: callable, newHeartbeatCallback: callable, newAckCallback: callable) -> None:
        self.db = sqlite3.connect("sms.db", check_same_thread=False)
        self.db.execute(
            "CREATE TABLE IF NOT EXISTS sms (sender TEXT, content TEXT, timestamp INTEGER)")
        self.db.commit()

        self._pw = os.environ["DONGLE_PASSWORD"]

        connection = AuthorizedConnection(
            f'http://admin:{self._pw}@192.168.8.1/')
        self.client = Client(connection)
        self.logger = setupLogger("SMS")

        self.logger.info("Dongle connection established")

        self.newQuestion = newQuestionCallback
        self.newURL = newURLCallback
        self.newHeartbeat = newHeartbeatCallback
        self.newAck = newAckCallback

    def listen(self):
        print("Listening for messages...")
        messages = {}
        while True:
            try:
                try:
                    messages = self.client.sms.get_sms_list()
                except:
                    self.logger.error("Error in get sms list.")
                    print("ERR SMS 01")
                    continue

                if messages["Messages"] and messages["Messages"]["Message"]:
                    for message in messages["Messages"]["Message"]:
                        sender = message["Phone"]
                        content = message["Content"]

                        self.logger.info("Sender: " + sender +
                                         ", Content: " + content)

                        result = self.processMessage(sender, content)
                        if (result):
                            self.db.execute(
                                "INSERT INTO sms (sender, content, timestamp) VALUES (?, ?, ?)", (sender, content, time.time()))
                            self.db.commit()

                            self.logger.info(
                                f"Received message: {message['Content']}")

                        try:
                            self.client.sms.delete_sms(message["Index"])
                        except:
                            self.logger.error("Error in delete SMS")
                            print("ERR SMS 02")
                            continue
                        time.sleep(1)
            except Exception:
                print("Error in SMS processing")
                print("ERR SMS 03")
                try:
                    self.client.user.logout()
                    time.sleep(2)
                    self.client.user.login(password=self._pw, force_new_login=True)
                except:
                    pass

            time.sleep(2)

    def processMessage(self, sender: str, text: str) -> bool:
        if len(text) == 0:
            self.logger.error("Empty message received")
            return False

        valid_prefixes = ["gpt:", "req:", "hbt:", "ack:"]

        if not text:
            return False
        if not any(text.lower().startswith(prefix) for prefix in valid_prefixes):
            self.logger.error("Invalid message received")
            return False

        if text.lower().startswith("req:"):
            # get url from text
            url_match = re.search(
                r"(?P<url>https?://[^\s]+\.[^\s]+|www\.[^\s]+\.[^\s]+)", text)
            url = url_match.group("url") if url_match else None

            if not url:
                return False
            if not url.startswith("http"):
                url = "http://" + url

            self.newURL(sender, url)
            return True

        if text.lower().startswith("gpt:"):
            # chatgpt call
            try:
                text = text.replace("GPT:", "")
                self.newQuestion(sender, text)
                return True
            except:
                self.logger.error("Failed to process question")
                return False

        if text.lower().startswith("hbt:"):
            # heartbeat
            self.logger.info("Heartbeat received from " + sender)
            self.newHeartbeat(sender, text)
            return True
        
        if text.lower().startswith("ack:"):
            # ack
            self.logger.info("Ack received from " + sender)
            self.newAck(sender, text)

        return True
