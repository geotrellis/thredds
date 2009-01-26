/*
 * Copyright 1997-2008 Unidata Program Center/University Corporation for
 * Atmospheric Research, P.O. Box 3000, Boulder, CO 80307,
 * support@unidata.ucar.edu.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package ucar.nc2.ft.point.standard;

import ucar.nc2.ft.point.*;
import ucar.nc2.ft.*;
import ucar.nc2.units.DateUnit;
import ucar.nc2.units.DateFormatter;
import ucar.ma2.StructureData;
import ucar.ma2.StructureDataIterator;

import java.io.IOException;
import java.util.Iterator;
import java.util.Date;
import java.util.List;

/**
 * Object Heirarchy:
 * StationProfileFeatureCollection (StandardStationProfileCollectionImpl)
 * StationProfileFeature (StandardStationProfileFeature)
 * ProfileFeature (StandardProfileFeature)
 * PointFeatureIterator (StandardPointFeatureIterator)
 * PointFeature
 *
 * @author caron
 * @since Mar 28, 2008
 */
public class StandardStationProfileCollectionImpl extends StationProfileCollectionImpl {
  private DateUnit timeUnit;
  private NestedTable ft;
  private DateFormatter dateFormatter = new DateFormatter();

  StandardStationProfileCollectionImpl(NestedTable ft, DateUnit timeUnit) throws IOException {
    super(ft.getName());
    this.ft = ft;
    this.timeUnit = timeUnit;
  }

  protected void initStations() {
    if (stationHelper != null) return;
    stationHelper = new StationHelper();

    try {
      List<Station> stnList = ft.makeStations(-1);
      for (Station s : stnList) {
        stationHelper.addStation(new StandardStationProfileFeature(s, null));
      }
    } catch (IOException ioe) {
      throw new RuntimeException("Failed to init stations", ioe);
    }
  }

  // iterate over stations
  public NestedPointFeatureCollectionIterator getNestedPointFeatureCollectionIterator(int bufferSize) throws IOException {
    return new NestedPointFeatureCollectionIterator() {
      private Iterator iter = getStations().iterator();
      public boolean hasNext() throws IOException {
        return iter.hasNext();
      }
      public NestedPointFeatureCollection next() throws IOException {
        return (StandardStationProfileFeature) iter.next();
      }
      public void setBufferSize(int bytes) {
      }
    };
  }

  // a time series of profiles at one station
  private class StandardStationProfileFeature extends StationProfileFeatureImpl {
    Station s;
    StructureData stationData;

    StandardStationProfileFeature(Station s, StructureData stationData) {
      super(s, StandardStationProfileCollectionImpl.this.timeUnit, -1);
      this.s = s;
      this.stationData = stationData;
    }

    // iterate over series of profiles
    public PointFeatureCollectionIterator getPointFeatureCollectionIterator(int bufferSize) throws IOException {
      Cursor cursor = new Cursor(ft.getNumberOfLevels());
      cursor.what = s;
      cursor.tableData[2] = stationData; // obs(leaf) = 0, profile=1, station(root)=2
      return new StandardStationProfileFeatureIterator(cursor);
    }

    private class StandardStationProfileFeatureIterator implements PointFeatureCollectionIterator {
      Cursor cursor;
      private ucar.ma2.StructureDataIterator iter;
      private int count = 0;

      StandardStationProfileFeatureIterator(Cursor cursor) throws IOException {
        this.cursor = cursor;
        iter = ft.getStationProfileDataIterator(cursor, -1);
      }

      public boolean hasNext() throws IOException {
        boolean r = iter.hasNext();
        if (!r)
          timeSeriesNpts = count; // field in StationProfileFeatureImpl
        count++;
        return r;
      }

      public PointFeatureCollection next() throws IOException {
        cursor.tableData[1] = iter.next();

        double time = ft.getObsTime(cursor);
        return new StandardProfileFeature(s, timeUnit.makeDate(time), cursor);
      }

      public void setBufferSize(int bytes) {
        iter.setBufferSize(bytes);
      }
    }
  }

  // one profile
  private class StandardProfileFeature extends ProfileFeatureImpl {
    private Cursor cursor;
    private String desc;

    StandardProfileFeature(Station s, Date time, Cursor cursor) throws IOException {
      super(dateFormatter.toDateTimeStringISO(time), s.getLatitude(), s.getLongitude(), -1);
      this.cursor = cursor;
      this.desc = "time=" + time + "stn=" + s.getDescription();
    }

    public String getDescription() {
      return desc;
    }

    // iterate over obs in the profile
    public PointFeatureIterator getPointFeatureIterator(int bufferSize) throws IOException {
      StructureDataIterator structIter = ft.getStationProfileObsDataIterator(cursor, bufferSize);
      return new StandardStationProfilePointIterator(structIter, cursor.copy());
    }

    // the iterator over the observations
    private class StandardStationProfilePointIterator extends StandardPointFeatureIterator {
      StationFeatureImpl station;

      StandardStationProfilePointIterator(StructureDataIterator structIter, Cursor cursor) throws IOException {
        super(ft, timeUnit, structIter, cursor, false);
      }

      // decorate to capture npts
      @Override
      public boolean hasNext() throws IOException {
        boolean result = super.hasNext();
        if (!result)
          setNumberPoints(getCount());
        return result;
      }
    }

  }

}
