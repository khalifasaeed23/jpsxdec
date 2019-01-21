/*
 * jPSXdec: PlayStation 1 Media Decoder/Converter in Java
 * Copyright (C) 2014-2019  Michael Sabin
 * All rights reserved.
 *
 * Redistribution and use of the jPSXdec code or any derivative works are
 * permitted provided that the following conditions are met:
 *
 *  * Redistributions may not be sold, nor may they be used in commercial
 *    or revenue-generating business activities.
 *
 *  * Redistributions that are modified from the original source must
 *    include the complete source code, including the source code for all
 *    components used by a binary built from the modified sources. However, as
 *    a special exception, the source code distributed need not include
 *    anything that is normally distributed (in either source or binary form)
 *    with the major components (compiler, kernel, and so on) of the operating
 *    system on which the executable runs, unless that component itself
 *    accompanies the executable.
 *
 *  * Redistributions must reproduce the above copyright notice, this list
 *    of conditions and the following disclaimer in the documentation and/or
 *    other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package jpsxdec.modules.dredd;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jpsxdec.cdreaders.CdFileSectorReader;
import jpsxdec.discitems.DiscItem;
import jpsxdec.discitems.SerializedDiscItem;
import jpsxdec.i18n.exception.LocalizedDeserializationFail;
import jpsxdec.i18n.log.DebugLogger;
import jpsxdec.i18n.log.ILocalizedLogger;
import jpsxdec.modules.IIdentifiedSector;
import jpsxdec.modules.SectorClaimSystem;
import jpsxdec.modules.video.Dimensions;
import jpsxdec.modules.video.IDemuxedFrame;
import jpsxdec.modules.video.ISectorClaimToDemuxedFrame;
import jpsxdec.modules.video.framenumber.FrameNumber;
import jpsxdec.modules.video.framenumber.IFrameNumberFormatter;
import jpsxdec.modules.video.framenumber.IndexSectorFrameNumber;
import jpsxdec.modules.video.sectorbased.DiscItemSectorBasedVideoStream;
import jpsxdec.modules.video.sectorbased.SectorBasedVideoInfo;
import jpsxdec.modules.xa.DiscItemXaAudioStream;

/** Represents Judge Dredd video streams.
 * Mostly identical to {@link jpsxdec.modules.strvideo.DiscItemStrVideoWithFrame}
 * except how it splits the audio. */
public class DiscItemDreddVideoStream extends DiscItemSectorBasedVideoStream {

    private static final Logger LOG = Logger.getLogger(DiscItemDreddVideoStream.class.getName());

    public static final String TYPE_ID = "Dredd";

    public DiscItemDreddVideoStream(@Nonnull CdFileSectorReader cd,
                                    int iStartSector, int iEndSector,
                                    @Nonnull Dimensions dim,
                                    @Nonnull IndexSectorFrameNumber.Format indexSectorFrameNumberFormat,
                                    @Nonnull SectorBasedVideoInfo strVidInfo)
    {
        super(cd, iStartSector, iEndSector, dim, indexSectorFrameNumberFormat, strVidInfo);
    }

    public DiscItemDreddVideoStream(@Nonnull CdFileSectorReader cd, @Nonnull SerializedDiscItem fields)
            throws LocalizedDeserializationFail
    {
        super(cd, fields);
    }


    @Override
    public @Nonnull String getSerializationTypeId() {
        return TYPE_ID;
    }

    @Override
    public int getParentRating(@Nonnull DiscItem child) {
        if (!(child instanceof DiscItemXaAudioStream))
            return 0;

        int iOverlapPercent = child.getOverlap(this)*100 / child.getSectorLength();
        if (iOverlapPercent > 0)
            iOverlapPercent += 100;
        return iOverlapPercent;
    }

    private static final int AUDIO_SPLIT_THRESHOLD = 32;

    @Override
    public int findAudioSplitPoint(@Nonnull DiscItemXaAudioStream audio) {
        int iStartSector = getStartSector();
        // if audio crosses the start sector
        if (audio.getStartSector() < iStartSector - AUDIO_SPLIT_THRESHOLD &&
            audio.getEndSector()  >= iStartSector)
        {
            return iStartSector - 2;
        } else {
            return -1;
        }
    }

    @Override
    public @Nonnull FrameNumber getStartFrame() {
        return _indexSectorFrameNumberFormat.getStartFrame();
    }

    @Override
    public FrameNumber getEndFrame() {
        return _indexSectorFrameNumberFormat.getEndFrame();
    }

    @Override
    public @Nonnull List<FrameNumber.Type> getFrameNumberTypes() {
        return Arrays.asList(FrameNumber.Type.Index, FrameNumber.Type.Sector);
    }

    @Override
    public void fpsDump(@Nonnull PrintStream ps) throws CdFileSectorReader.CdReadException {
        SectorClaimSystem it = createClaimSystem();
        int iFrameIndex = 0;
        int iLastChunk = -1;
        for (int iSector = 0; it.hasNext(); iSector++) {
            IIdentifiedSector isect = it.next(DebugLogger.Log).getClaimer();
            if (isect instanceof SectorDreddVideo) {
                SectorDreddVideo vidSect = (SectorDreddVideo) isect;
                ps.println(String.format("%-5d %-4d %d/%d",
                                        iSector,
                                        iFrameIndex,
                                        vidSect.getChunkNumber(),
                                        vidSect.getChunksInFrame() ));
                if (vidSect.getChunkNumber() < iLastChunk)
                    iFrameIndex++;
                iLastChunk = vidSect.getChunkNumber();
            } else {
                ps.println(String.format(
                        "%-5d X",
                        iSector));
            }

        }
    }

    @Override
    public @Nonnull ISectorClaimToDemuxedFrame makeDemuxer() {
        return new Demuxer(_indexSectorFrameNumberFormat.makeFormatter(),
                           getStartSector(), getEndSector());
    }


    public static class Demuxer implements ISectorClaimToDemuxedFrame, SectorClaimToDreddFrame.Listener {

        @Nonnull
        private final IFrameNumberFormatter _indexSectorFrameNumberFormatter;
        @CheckForNull
        private IDemuxedFrame.Listener _listener;
        private final int _iStartSector, _iInclusiveEndSector;

        private Demuxer(@Nonnull IFrameNumberFormatter indexSectorFrameNumberFormatter,
                        int iStartSector, int iInclusiveEndSector)
        {
            _indexSectorFrameNumberFormatter = indexSectorFrameNumberFormatter;
            _iStartSector = iStartSector;
            _iInclusiveEndSector = iInclusiveEndSector;
        }

        public void attachToSectorClaimer(@Nonnull SectorClaimSystem scs) {
            SectorClaimToDreddFrame s2df = scs.getClaimer(SectorClaimToDreddFrame.class);
            s2df.setListener(this);
            s2df.setRangeLimit(_iStartSector, _iInclusiveEndSector);
        }

        public void setFrameListener(@Nonnull IDemuxedFrame.Listener listener) {
            _listener = listener;
        }

        public void frameComplete(@Nonnull DemuxedDreddFrame frame, @Nonnull ILocalizedLogger log) {
            FrameNumber fn = _indexSectorFrameNumberFormatter.next(frame.getStartSector(), log);
            frame.setFrame(fn);
            if (_listener != null)
                _listener.frameComplete(frame);
        }

        public void videoBreak(@Nonnull ILocalizedLogger log) {
            LOG.info("Dredd video break while saving");
        }

        public void endOfSectors(@Nonnull ILocalizedLogger log) {
            
        }
    }
    
}
