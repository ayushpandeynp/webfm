# Author: Ayush Pandey (ayush.pandey@nyu.edu)
# Utility functions for the server.

import string
import random
from datetime import datetime
from datetime import datetime, time as t

def get_day_of_month(time_in_sec):
    date_time = datetime.fromtimestamp(time_in_sec)
    return date_time.day

def randomFileName(N: int) -> str:
    return ''.join(random.choices(string.ascii_lowercase + string.digits, k=N))

def is_time_between(h1, m1, h2, m2):
    now = datetime.now().time()
    
    start_time = t(h1, m1)
    end_time = t(h2, m2)
    
    if start_time <= end_time:
        return start_time <= now <= end_time
    else:
        return now >= start_time or now <= end_time
        