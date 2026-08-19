package com.ayush.quietlib.rfm.service.recording;

import com.ayush.quietlib.rfm.service.fm.RecordError;

/**
 * vlad805 (c) 2020
 */
public interface IFMRecorder {
	void startRecord() throws RecordError;
	void record(final short[] data, final int length);
	void stopRecord();
}
