package com.google.android.exoplayer2.trackselection;


import android.os.SystemClock;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.upstream.BandwidthMeter;

import hugo.weaving.DebugLog;
import timber.log.Timber;

public class MullerTrackSelection extends BaseTrackSelection {

  /**
   * Factory for {@link AdaptiveTrackSelection} instances.
   */
  public static final class Factory implements TrackSelection.Factory {

    private final BandwidthMeter bandwidthMeter;
    private final int maxInitialBitrate;
    private final float bandwidthFraction;

    /**
     * @param bandwidthMeter Provides an estimate of the currently available bandwidth.
     */
    public Factory(BandwidthMeter bandwidthMeter) {
      this(bandwidthMeter, DEFAULT_MAX_INITIAL_BITRATE, DEFAULT_BANDWIDTH_FRACTION);
    }

    /**
     * @param bandwidthMeter    Provides an estimate of the currently available bandwidth.
     * @param maxInitialBitrate The maximum bitrate in bits per second that should be assumed
     *                          when a bandwidth estimate is unavailable.
     * @param bandwidthFraction The fraction of the available bandwidth that the selection should
     *                          consider available for use. Setting to a value less than 1 is recommended to account
     *                          for inaccuracies in the bandwidth estimator.
     */
    public Factory(BandwidthMeter bandwidthMeter, int maxInitialBitrate, float bandwidthFraction) {
      this.bandwidthMeter = bandwidthMeter;
      this.maxInitialBitrate = maxInitialBitrate;
      this.bandwidthFraction = bandwidthFraction;
    }

    @Override
    public MullerTrackSelection createTrackSelection(TrackGroup group, int... tracks) {
      return new MullerTrackSelection(group, tracks, bandwidthMeter, maxInitialBitrate, bandwidthFraction);
    }

  }


  public static final int DEFAULT_MAX_INITIAL_BITRATE = 800000;
  public static final float DEFAULT_BANDWIDTH_FRACTION = 1f;

  private static final int DEFAULT_BUFFER_SIZE_S = 30;
  private static final float BUFFER_0_15_THRESHOLD = 0.15f;
  private static final float BUFFER_0_30_THRESHOLD = 0.35f;
  private static final float BUFFER_0_50_THRESHOLD = 0.50f;

  private static final float BUFFER_0_3_MULTIPLIER = 0.3f;
  private static final float BUFFER_0_5_MULTIPLIER = 0.5f;
  private static final float BUFFER_1_MULTIPLIER = 1f;
  private static final float BUFFER_0_50_EXTRA = 0.5f;

  private final BandwidthMeter bandwidthMeter;
  private final int maxInitialBitrate;
  private final float bandwidthFraction;

  private int selectedIndex;
  private int reason;


  /**
   * @param group          The {@link TrackGroup}.
   * @param tracks         The indices of the selected tracks within the {@link TrackGroup}. Must not be
   *                       empty. May be in any order.
   * @param bandwidthMeter Provides an estimate of the currently available bandwidth.
   */
  public MullerTrackSelection(TrackGroup group, int[] tracks,
                              BandwidthMeter bandwidthMeter) {
    this(group, tracks, bandwidthMeter, DEFAULT_MAX_INITIAL_BITRATE, DEFAULT_BANDWIDTH_FRACTION);
  }

  /**
   * @param group             The {@link TrackGroup}.
   * @param tracks            The indices of the selected tracks within the {@link TrackGroup}. Must not be
   *                          empty. May be in any order.
   * @param bandwidthMeter    Provides an estimate of the currently available bandwidth.
   * @param maxInitialBitrate The maximum bitrate in bits per second that should be assumed when a
   *                          bandwidth estimate is unavailable.
   * @param bandwidthFraction The fraction of the available bandwidth that the selection should
   *                          consider available for use. Setting to a value less than 1 is recommended to account
   *                          for inaccuracies in the bandwidth estimator.
   */
  public MullerTrackSelection(TrackGroup group, int[] tracks, BandwidthMeter bandwidthMeter,
                              int maxInitialBitrate, float bandwidthFraction) {
    super(group, tracks);
    this.bandwidthMeter = bandwidthMeter;
    this.maxInitialBitrate = maxInitialBitrate;
    this.bandwidthFraction = bandwidthFraction;
    reason = C.SELECTION_REASON_INITIAL;
  }

  @Override
  public int getSelectedIndex() {
    return selectedIndex;
  }

  @Override
  public int getSelectionReason() {
    return reason;
  }

  @Override
  public Object getSelectionData() {
    return null;
  }

  @Override
  @DebugLog
  public void updateSelectedTrack(long bufferedDurationUs, long playbackPositionUs, long bufferEndTime) {
    long nowMs = SystemClock.elapsedRealtime();

    int currentSelectedIndex = selectedIndex;

    // get buffer length
    long bufferLength = bufferEndTime - playbackPositionUs;
    float bufferLengthS = (float) bufferLength / 1000000f;
    float bufferPercent = bufferLengthS / DEFAULT_BUFFER_SIZE_S;
    Timber.d("Buffer Length: %f", bufferLengthS);
    Timber.d("Buffer percent: %f", bufferPercent);


    // get bandwidth estimation
    long bitrateEstimate = bandwidthMeter.getBitrateEstimate();
    long effectiveBitrate = bitrateEstimate == BandwidthMeter.NO_ESTIMATE
        ? maxInitialBitrate : (long) (bitrateEstimate * bandwidthFraction);
    Timber.d("Estimated bitrate: %d", effectiveBitrate);

    // update bandwidth estimation with with muller algorithm
    if (bufferLengthS < (float) DEFAULT_BUFFER_SIZE_S * BUFFER_0_15_THRESHOLD) {
      Timber.d("1");
      effectiveBitrate = (long) (effectiveBitrate * BUFFER_0_3_MULTIPLIER);

    } else if (bufferLengthS < (float) DEFAULT_BUFFER_SIZE_S * BUFFER_0_30_THRESHOLD) {
      Timber.d("2");
      effectiveBitrate = (long) (effectiveBitrate * BUFFER_0_5_MULTIPLIER);

    } else if (bufferLengthS < (float) DEFAULT_BUFFER_SIZE_S * BUFFER_0_50_THRESHOLD) {
      Timber.d("3");
      effectiveBitrate = (long) (effectiveBitrate * BUFFER_1_MULTIPLIER);

    } else {
      Timber.d("4");
      effectiveBitrate = (long) (effectiveBitrate * (BUFFER_1_MULTIPLIER + (BUFFER_0_50_EXTRA * bufferPercent )));
    }

    Timber.d("Muller estimated bitrate: %d", effectiveBitrate);

    selectedIndex = determineIdealSelectedIndex(nowMs, effectiveBitrate);
    Timber.d("Selected index: " + selectedIndex);


    // If we adapted, update the trigger.
    if (selectedIndex != currentSelectedIndex) {
      reason = C.SELECTION_REASON_ADAPTIVE;
    }
  }

  /**
   * Computes the ideal selected index ignoring buffer health.
   *
   * @param nowMs The current time in the timebase of {@link SystemClock#elapsedRealtime()}, or
   *              {@link Long#MIN_VALUE} to ignore blacklisting.
   */
  private int determineIdealSelectedIndex(long nowMs, long effectiveBitrate) {
    int lowestBitrateNonBlacklistedIndex = 0;
    for (int i = 0; i < length; i++) {
      if (nowMs == Long.MIN_VALUE || !isBlacklisted(i, nowMs)) {
        Format format = getFormat(i);
        if (format.bitrate <= effectiveBitrate) {
          return i;
        } else {
          lowestBitrateNonBlacklistedIndex = i;
        }
      }
    }
    return lowestBitrateNonBlacklistedIndex;
  }
}
