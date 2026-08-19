package com.ayush.quietlib.rfm.service.recording;

import com.ayush.quietlib.rfm.service.fm.RecordError;

/**
 * vlad805 (c) 2020
 */
public interface IAudioRecordable {
	void startRecord(final IFMRecorder driver) throws RecordError;
	void stopRecord();
}
