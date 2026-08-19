# Author: Ayush Pandey (ayush.pandey@nyu.edu)
# This class encodes images and GPT responses into a custom binary format (.sonic) for transmission.

import os
import zlib
from PIL import Image
import time
import math
from ChatGPT import ChatGPT
import json

class SonicEncoder:
    def __init__(self) -> None:
        self.partitionWidth = 10  # 10px wide partitions, from left to right of the image
        self.frameSize = 500  # 500 bytes per frame

    def calculateChecksum(self, url: str) -> str:
        timestamp = int(time.time())
        input_string = f"{url}{timestamp}"
        crc32_hash = zlib.crc32(input_string.encode())
        return f"{crc32_hash:08x}"

    def createPartitions(self, screenshotFile: str) -> str:
        img = Image.open(screenshotFile)
        width, height = img.size

        partitions = []
        for i in range(0, width, self.partitionWidth):
            partitions.append(
                img.crop((i, 0, i + self.partitionWidth, height)))

        # save each partition to tmp folder in appropriate format
        os.makedirs("tmp", exist_ok=True)

        for i, partition in enumerate(partitions):
            if height > 16383:
                partition.save(f"tmp/partition{i}.jpg", "JPEG")
            else:
                partition.save(f"tmp/partition{i}.webp", "WEBP")

        partition_files = [
            f"tmp/partition{i}.{'jpg' if height > 16383 else 'webp'}" for i in range(len(partitions))]
        partition_bytes = []

        for file in partition_files:
            with open(file, "rb") as f:
                partition_bytes.append(bytearray(f.read()))

        # delete the partition files
        for file in partition_files:
            os.remove(file)

        return partition_bytes, width, height

    def encode(self, screenshotFile: str, destinationFile: str, url: str) -> None:
        linkFile = screenshotFile.replace(".png", ".json")
        
        linkData = ""
        if os.path.exists(linkFile):
            with open(linkFile, "r") as f:
                linkData = f.read()
                
                try:
                    json.loads(linkData)
                except:
                    linkData = ""

        if linkData != "":
            response = linkData.encode()
            chunks = math.ceil(len(response) / (self.frameSize - 4))
            response = [response[i * (self.frameSize - 4):min(
                (i + 1) * (self.frameSize - 4), len(response))] for i in range(chunks)]

            if len(response[-1]) < self.frameSize - 4:
                response[-1] = response[-1] + (" " * \
                    (self.frameSize - 4 - len(response[-1]))).encode()
                
        partitions, w, h = self.createPartitions(screenshotFile)
        checksum = self.calculateChecksum(url)
        metadata: str = "MDTA" + "img" + \
            ",".join([str(len(i)) for i in partitions]) + \
            "URL" + url + "CKSM" + checksum + \
            "W" + str(w) + "H" + str(h) + "EOMD"
        metadata = metadata + " " * (self.frameSize - len(metadata))


        with open(destinationFile, "wb") as f:
            f.write(metadata.encode())

            f.write(("LNKS" + " " * (self.frameSize - 4)).encode())

            if linkData != "":
                for l in response:
                    f.write("C137".encode())
                    f.write(l)
            else:
                f.write("[]".encode())
            
            f.write(("SDTA" + " " * (self.frameSize - 4)).encode())

            __frameSize = self.frameSize - 12

            for i, p in enumerate(partitions):
                partitionSize = len(p)

                fc = 0
                fs = 0

                while (fs < partitionSize):
                    frameIndex = str(fc).zfill(5).encode()
                    partitionIndex = str(i).zfill(3).encode()

                    f.write("C137".encode())
                    f.write(frameIndex)
                    f.write(partitionIndex)

                    end = min(fs + __frameSize, partitionSize)
                    f.write(p[fs:end])

                    if end - fs < __frameSize:
                        f.write((" " * (__frameSize - (end - fs))).encode())

                    fs += min(__frameSize, partitionSize)
                    fc += 1

            f.write("CKSM".encode())
            f.write(str(checksum).encode())

            f.write("EOF".encode())
            f.write((" " * (self.frameSize - 15)).encode())

    def encodeGPT(self, destinationFile: str, question: str) -> None:
        checksum = self.calculateChecksum(question)
        metadata: str = "MDTA" + "gpt" + \
            "00" + "URL" + question + "CKSM" + checksum + "W0H0" + "EOMD"
        metadata = metadata + " " * (self.frameSize - len(metadata))

        chatgpt = ChatGPT()
        try:
            response = chatgpt.ask(question).encode()
            chunks = math.ceil(len(response) / (self.frameSize - 4))
            response = [response[i * (self.frameSize - 4):min(
                (i + 1) * (self.frameSize - 4), len(response))] for i in range(chunks)]

            if len(response[-1]) < self.frameSize - 4:
                response[-1] = response[-1] + (" " * \
                    (self.frameSize - 4 - len(response[-1]))).encode()
        except Exception as e:
            print(e)
            return

        with open(destinationFile, "wb") as f:
            f.write(metadata.encode())
            f.write(("SDTA" + " " * (self.frameSize - 4)).encode())

            for r in response:
                f.write("C137".encode())
                f.write(r)

            f.write("CKSM".encode())
            f.write(str(checksum).encode())
            f.write("EOF".encode())
            f.write((" " * (self.frameSize - 15)).encode())
