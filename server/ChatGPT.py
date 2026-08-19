# Author: Ayush Pandey (ayush.pandey@nyu.edu)
# This class handles communication with the OpenAI Chat Completions API.

from openai import OpenAI
from dotenv import load_dotenv
import os

load_dotenv()

class ChatGPT:
    def __init__(self):
        OpenAI.api_key = os.getenv("OPENAI_API_KEY")
        
        self.client = OpenAI()

    def ask(self, question):
        response = self.client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[{"role": "user", "content": question}],
            max_tokens=5000,
        )
        return response.choices[0].message.content