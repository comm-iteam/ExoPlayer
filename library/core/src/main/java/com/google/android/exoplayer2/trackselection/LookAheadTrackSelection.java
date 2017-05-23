package com.google.android.exoplayer2.trackselection;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.upstream.BandwidthMeter;

import timber.log.Timber;

/**
 * Create by Ismael on 16-May-17.
 */

public class LookAheadTrackSelection extends BaseTrackSelection {

  private final boolean V = true;

  // bytes. NOTE: Only valid for Elephant's Dream
  private int chunkSizes[][] = {
      {7667797, 7179078, 10170863, 17683639, 15072662, 14529482, 5111726, 16549378, 18851768, 72106282, 54933067, 24383499, 7365758, 5210616, 16231324, 7592712, 6289471, 7703194, 5998696, 6389021, 5474164, 6435880, 8406105, 5773928, 12869185},
      {3669846, 3465268,  5674955, 9874238, 8434560, 8143672, 2883569, 9183559, 11318120, 38908019, 28974419, 12665731, 3196705, 2693392, 8379028, 3530115, 3047289, 4112581, 3113877, 3556249, 3038707, 3419333, 4419206, 3035841, 6537461},
      { 726898,  629092,  1459201, 2020259, 1466767, 1583434, 675248, 1932774, 2608417, 6548244, 4337952, 2501037, 554945, 563846, 1872613, 731610, 687828, 1047670, 725281, 951203, 810984, 813243, 1138388, 664442, 1351615}
  };

  //seconds. NOTE: Only valid for Elephant's Dream
  private double chunkTimeRanges[][] = {
    {0.00, 10.00, 20.00, 30.28, 40.28, 52.84, 66.92, 76.92, 94.12, 106.52, 122.16, 133.64, 146.32, 156.80, 167.36, 182.16, 196.64, 207.92, 223.84, 234.72, 245.12, 257.32, 268.52, 280.76, 290.76},
    {10.0, 20.0, 30.28, 40.28, 52.84, 66.92, 76.92, 94.12, 106.52, 122.16, 133.64, 146.32, 156.80, 167.36, 182.16, 196.64, 207.92, 223.84, 234.72, 245.12, 257.32, 268.52, 280.76, 290.76, 300.00}
  };

  //seconds. NOTE: Only valid for Elephant's Dream
  private double chunkDuration[]= {10.00, 10.00, 10.28, 10.00, 12.56, 14.08, 10.00, 17.20, 12.40, 15.64, 11.48, 12.68, 10.48, 10.56, 14.80, 14.48, 11.28, 15.92, 10.88, 10.40, 12.20, 11.20, 12.24, 10.00, 9.24};

  private int selectedIndex = 0;
  private int reason;

  private BandwidthMeter bandwidthMeter;

  /**
   * @param group  The {@link TrackGroup}. Must not be null.
   * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
   */
  public LookAheadTrackSelection(TrackGroup group, int[] tracks, BandwidthMeter bandwidthMeter) {
    super(group, tracks);
    this.bandwidthMeter = bandwidthMeter;
  }

  @Override
  public int getSelectedIndex() {
    if (V)  Timber.d("COMMLA: getSelectedIndex: %d", selectedIndex);
    return selectedIndex;
  }

  @Override
  public int getSelectionReason() {
    return C.SELECTION_REASON_ADAPTIVE;
  }

  @Override
  public Object getSelectionData() {
    return null;
  }

  @Override
  public void updateSelectedTrack(long bufferedDurationUs) {
    if (V)  Timber.d("COMMLA: bandwidth(kB/s): %d", (bandwidthMeter.getBitrateEstimate() / 8) / 1024 );


    selectedIndex = ++selectedIndex % chunkSizes.length;

    Format currentFormat = getSelectedFormat();
    if (V)  Timber.d("COMMLA: updateSelectedTrack | Format: %s", currentFormat);

    if (V) Timber.d("COMMLA: updateSelectedTrack | NumberOfSegment: %d", getNumberOfSegment(System.currentTimeMillis()));

  }


  /**
   * Factory for {@link AdaptiveTrackSelection} instances.
   */
  public static final class Factory implements TrackSelection.Factory {

    private final BandwidthMeter bandwidthMeter;

    public Factory(BandwidthMeter bandwidthMeter) {
      this.bandwidthMeter = bandwidthMeter;
    }

    @Override
    public LookAheadTrackSelection createTrackSelection(TrackGroup group, int... tracks) {
      return new LookAheadTrackSelection(group, tracks, bandwidthMeter);
    }

  }

  public int getNumberOfSegment (float instantOfTime){
    System.out.println("instantOfTime: "+instantOfTime);
    for (int i=0; i<chunkTimeRanges[0].length; i++){
      if (chunkTimeRanges[0][i]>instantOfTime){
        return i;
      }
    }
    return -1;
  }

}
