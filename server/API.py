# Author: Ayush Pandey (ayush.pandey@nyu.edu)
# This file defines a simple REST API using Flask to handle incoming api_call messages and process them accordingly.
# This is to be used for testing when there is no dongle available for SMS communication.
 
from flask import Flask, request, jsonify
import re
from Manager import Manager
import logger

app = Flask(__name__, static_folder="static", static_url_path="/download")
log = logger.setupLogger("API")

manager = Manager()
manager.manage()

def processMessage(sender: str, text: str) -> bool:
        if len(text) == 0:
            log.error("Empty message received")
            return False

        valid_prefixes = ["gpt:", "req:", "hbt:", "ack:"]
        if not any(text.lower().startswith(prefix) for prefix in valid_prefixes):
            log.error("Invalid message received")
            return False

        if text.lower().startswith("req:"):
            # get url from text
            url_match = re.search(
                r"(?P<url>https?://[^\s]+\.[^\s]+|www\.[^\s]+\.[^\s]+)", text)
            url = url_match.group("url") if url_match else None

            if not url.startswith("http"):
                url = "http://" + url

            manager.newURL(sender, url)
            return True

        if text.lower().startswith("gpt:"):
            # chatgpt call
            try:
                text = text.replace("GPT:", "")
                manager.newQuestion(sender, text)
                return True
            except:
                log.error("Failed to process question")
                return False

        if text.lower().startswith("hbt:"):
            # heartbeat
            log.info("Heartbeat received from " + sender)
            manager.newHeartbeat(sender, text)
            return True
        
        if text.lower().startswith("ack:"):
            # ack
            log.info("Ack received from " + sender)
            manager.newAck(sender, text)

        return True

# home
@app.route('/')
def home():
    return "SONIC"

# simulates receiveing of a new SMS.
@app.route('/new-sms', methods=['POST'])
def newSMS():
    text = request.json['text']

    if len(text) == 0:
        return jsonify({'success': False, 'error': 'No text found in SMS'}), 400

    processMessage("TEST", text)

    # respond with status 200
    return jsonify({'success': True, 'requested': text}), 200


def main():
    log.info("API started")
    app.run(debug=True, use_reloader=False, host='0.0.0.0', port=5111)


if __name__ == '__main__':
    main()