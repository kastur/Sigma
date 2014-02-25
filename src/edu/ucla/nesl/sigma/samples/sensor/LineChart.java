package edu.ucla.nesl.sigma.samples.sensor;

import android.content.Context;
import android.graphics.Color;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

public class LineChart {

  public static final int X_AXIS_MAX = 1500;

  final XYMultipleSeriesDataset mMultiDataset;
  final XYMultipleSeriesRenderer mMultiRenderer;
  final GraphicalView mView;

  public synchronized XYSeries addSeries(String name, int color) {
    XYSeries s = new XYSeries(name);
    mMultiDataset.addSeries(s);
    XYSeriesRenderer r = new XYSeriesRenderer();
    r.setColor(color);
    r.setPointStyle(PointStyle.CIRCLE);
    r.setPointStrokeWidth(3);
    r.setFillPoints(true);
    //r.setLineWidth(1);
    r.setDisplayChartValues(false);
    mMultiRenderer.addSeriesRenderer(r);
    return s;
  }

  public LineChart(Context context) {
    mMultiRenderer = new XYMultipleSeriesRenderer();
    mMultiDataset = new XYMultipleSeriesDataset();
    //mMultiRenderer.setXLabels(0);
    mMultiRenderer.setLabelsColor(Color.RED);
    mMultiRenderer.setXTitle("time");
    mMultiRenderer.setYTitle("accel");
    mMultiRenderer.setZoomButtonsVisible(true);
    mMultiRenderer.setYAxisMin(-10.0);
    mMultiRenderer.setYAxisMax(+10.0);
    mMultiRenderer.setXAxisMin(0);
    mMultiRenderer.setXAxisMax(X_AXIS_MAX);
    mView = ChartFactory.getLineChartView(context, mMultiDataset, mMultiRenderer);
  }

  public GraphicalView getView() {
    return mView;
  }
}
