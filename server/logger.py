# Author: Ayush Pandey (ayush.pandey@nyu.edu)
# This module sets up logging for the server.

import logging

def setupLogger(name):
    logger = logging.getLogger(name)
    logger.setLevel(logging.DEBUG)

    handler = logging.FileHandler('logs.log')
    handler.setLevel(logging.DEBUG)
    formatter = logging.Formatter('%(name)s-%(levelname)s %(asctime)s: %(message)s')
    handler.setFormatter(formatter)

    logger.addHandler(handler)

    return logger