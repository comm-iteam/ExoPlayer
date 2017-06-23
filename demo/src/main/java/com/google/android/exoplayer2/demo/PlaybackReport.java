package com.google.android.exoplayer2.demo;


import java.io.Serializable;

public class PlaybackReport implements Serializable {

  public PlaybackReport(int stops, long stallTime, long initialBuffering) {
    this.stops = stops;
    this.stallTime = stallTime;
    this.initialBuffering = initialBuffering;
  }

  private int stops;
  private long stallTime;
  private long initialBuffering;


  public int getStops() {
    return stops;
  }

  public void setStops(int stops) {
    this.stops = stops;
  }

  public long getStallTime() {
    return stallTime;
  }

  public void setStallTime(long stallTime) {
    this.stallTime = stallTime;
  }

  public long getInitialBuffering() {
    return initialBuffering;
  }

  public void setInitialBuffering(long initialBuffering) {
    this.initialBuffering = initialBuffering;
  }
}
