# Author: Ayush Pandey (ayush.pandey@nyu.edu)
# This class collects and stores system metrics such as battery status and weather information.

import psutil
import sqlite3
import time
import openmeteo_requests
from config import *
from logger import setupLogger

class Metrics:
    def __init__(self) -> None:
        self.logger = setupLogger("Metrics")
        self.db = sqlite3.connect("metrics.db", check_same_thread=False)
        self.db.execute(
            "CREATE TABLE IF NOT EXISTS metrics (id INTEGER PRIMARY KEY, type TEXT, data TEXT, timestamp INTEGER)")
        self.db.commit()

    def collect(self):
        while True:
            self.battery_status()
            self.weather()
            time.sleep(300)

    def weather(self):
        try:
            openmeteo = openmeteo_requests.Client()

            url = "https://api.open-meteo.com/v1/forecast"
            params = {
                "latitude": SERVER_LATITUDE,
                "longitude": SERVER_LONGITUDE,
                "current": ["temperature_2m", "relative_humidity_2m", "apparent_temperature", "precipitation", "rain", "showers", "snowfall", "apparent_temperature"],
                "timezone": "auto",
            }

            responses = openmeteo.weather_api(url, params=params)

            temperature = responses[0].Current().Variables(0).Value()
            humidity = responses[0].Current().Variables(1).Value()
            apparent_temperature = responses[0].Current().Variables(2).Value()
            precipitation = responses[0].Current().Variables(3).Value()
            rain = responses[0].Current().Variables(4).Value()
            showers = responses[0].Current().Variables(5).Value()
            snowfall = responses[0].Current().Variables(6).Value()
            apparent_temperature = responses[0].Current().Variables(7).Value()

            data = f"{temperature}_{humidity}_{apparent_temperature}_{precipitation}_{rain}_{showers}_{snowfall}_{apparent_temperature}"
            self.db.execute("INSERT INTO metrics (type, data, timestamp) VALUES (?, ?, ?)",
                            ("weather", data, int(time.time())))
            self.db.commit()
        except Exception:
            self.logger.error("Weather API failed")

    def battery_status(self):
        data = None
        battery = psutil.sensors_battery()

        if battery is None:
            return

        percent = battery.percent
        plugged = battery.power_plugged

        data = str(percent) + "_" + str(plugged)

        self.db.execute("INSERT INTO metrics (type, data, timestamp) VALUES (?, ?, ?)",
                        ("battery", data, int(time.time())))
        self.db.commit()
