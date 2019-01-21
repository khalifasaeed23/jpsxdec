/*
 * jPSXdec: PlayStation 1 Media Decoder/Converter in Java
 * Copyright (C) 2017-2019  Michael Sabin
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

package jpsxdec.modules.iso9660;

import java.io.IOException;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jpsxdec.cdreaders.CdSector;
import jpsxdec.i18n.log.ILocalizedLogger;
import jpsxdec.modules.IdentifiedSector;
import jpsxdec.modules.SectorClaimSystem;
import jpsxdec.util.IOIterator;


public class SectorClaimToSectorISO9660 extends SectorClaimSystem.SectorClaimer {

    public interface Listener {
        /** @param idSector Either {@link SectorISO9660VolumePrimaryDescriptor} or
         *                         {@link SectorISO9660DirectoryRecords} only. */
        void isoSectorRead(@Nonnull CdSector cdSector, @CheckForNull IdentifiedSector idSector);
        void endOfSectors(@Nonnull ILocalizedLogger log);
    }

    private static @CheckForNull IdentifiedSector id(@Nonnull CdSector sector) {
        IdentifiedSector id;
        if ((id = new SectorISO9660VolumePrimaryDescriptor(sector)).getProbability() > 0) return id;
        if ((id = new SectorISO9660DirectoryRecords(sector)).getProbability() > 0) return id;
        return null;
    }
    
    @CheckForNull
    private Listener _listener;

    public SectorClaimToSectorISO9660() {
    }
    public SectorClaimToSectorISO9660(@Nonnull Listener listener) {
        _listener = listener;
    }
    public void setListener(@Nonnull Listener listener) {
        _listener = listener;
    }

    public void sectorRead(@Nonnull SectorClaimSystem.ClaimableSector cs,
                           @Nonnull IOIterator<SectorClaimSystem.ClaimableSector> csit,
                           @Nonnull ILocalizedLogger log)
            throws IOException
    {
        IdentifiedSector idSector;
        if (cs.isClaimed()) {
            idSector = null;
        } else {
            idSector = id(cs.getSector());
            if (idSector != null)
                cs.claim(idSector);
        }

        if (_listener != null && sectorIsInRange(cs.getSector().getSectorIndexFromStart()))
            _listener.isoSectorRead(cs.getSector(), idSector);
    }

    public void endOfSectors(@Nonnull ILocalizedLogger log) {
        if (_listener != null)
            _listener.endOfSectors(log);
    }
}
