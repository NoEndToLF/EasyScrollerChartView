package com.wxy.easyscrollerchartviewlibrary;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Xfermode;
import android.support.annotation.Nullable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Scroller;

import com.wxy.easyscrollerchartviewlibrary.model.ScrollerPointModel;

import java.util.List;


public class EasyScrollerChartView extends View {
    private final String TAG = getClass().getName();
    //设置默认的宽和高,比例为4:3
    private static final int DEFUALT_VIEW_WIDTH=400;
    private static final int DEFUALT_VIEW_HEIGHT=300;
    private Paint horizontalLinePaint;
    private Paint verticalLinePaint;
    private TextPaint horizontalTextPaint ;
    private TextPaint verticalTextPaint;
    private TextPaint pointTextPaint;
    private float verticalMin= 0;//纵坐标最小值
    private float verticalMax= 0;//纵坐标最大值
    private float horizontalMin= 0;//横坐标最小值
    private float horizontalAverageWeight= 0;//横坐标每个平均区间代表多少值
    private float horizontalRatio=0.2f;
    private List<String> horizontalCoordinatesList;
    private List<String> verticalCoordinatesList;
    private List<? extends ScrollerPointModel> scrollerPointModelList;
    private boolean isScoll;
    private int mLastX = 0;
    private boolean isFling;
    private int downX=0,downY=0;
    private VelocityTracker mVelocityTracker;
    private Scroller scroller;
    private float horizontalAverageWidth;
    private float verticalRegionLength=0;//纵坐标有限区间长度
    private float verticalTextoffsetX;
    private Point originalPoint;
    public EasyScrollerChartView(Context context) {
        super(context);
    }

    public EasyScrollerChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        //横坐标轴的画笔
        horizontalLinePaint=new Paint();
        horizontalLinePaint.setAntiAlias(true);
        horizontalLinePaint.setColor(Color.BLACK);
        horizontalLinePaint.setStrokeWidth(2);

        //纵坐标轴的画笔
        verticalLinePaint=new Paint();
        verticalLinePaint.setAntiAlias(true);
        verticalLinePaint.setColor(Color.BLACK);
        verticalLinePaint.setStrokeWidth(2);

        //横坐标的刻度值画笔
        horizontalTextPaint= new TextPaint();
        horizontalTextPaint.setTextSize(40);
        horizontalTextPaint.setColor(Color.BLACK);

        //纵坐标的刻度值画笔
        verticalTextPaint= new TextPaint();
        verticalTextPaint.setTextAlign(Paint.Align.RIGHT);
        verticalTextPaint.setTextSize(40);
        verticalTextPaint.setColor(Color.BLACK);

        //每个点的值画笔
        pointTextPaint= new TextPaint();
        pointTextPaint.setTextAlign(Paint.Align.CENTER);
        pointTextPaint.setTextSize(40);
        pointTextPaint.setColor(Color.BLACK);

        scroller=new Scroller(context);
    }

    public EasyScrollerChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width=0,height=0;
        int width_specMode= MeasureSpec.getMode(widthMeasureSpec);
        int height_specMode= MeasureSpec.getMode(heightMeasureSpec);
        switch (width_specMode){
            //宽度精确值
            case MeasureSpec.EXACTLY:
                switch (height_specMode){
                    //高度精确值
                    case MeasureSpec.EXACTLY:
                        width= MeasureSpec.getSize(widthMeasureSpec);
                        height= MeasureSpec.getSize(heightMeasureSpec);
                        break;
                    case MeasureSpec.AT_MOST:
                    case MeasureSpec.UNSPECIFIED:
                        width= MeasureSpec.getSize(widthMeasureSpec);
                        height=width*3/4;
                        break;
                }
                break;
            //宽度wrap_content
            case MeasureSpec.AT_MOST:
            case MeasureSpec.UNSPECIFIED:
                switch (height_specMode){
                    //高度精确值
                    case MeasureSpec.EXACTLY:
                        height= MeasureSpec.getSize(heightMeasureSpec);
                        width=height*4/3;
                        break;
                    case MeasureSpec.AT_MOST:
                    case MeasureSpec.UNSPECIFIED:
                        height=DEFUALT_VIEW_HEIGHT;
                        width=DEFUALT_VIEW_WIDTH;
                        break;
                }
                break;
        }
        setMeasuredDimension(width,height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (verticalCoordinatesList==null){
            Log.v(TAG,"请初始化纵坐标刻度值，setVerticalCoordinatesList()");
            return;
        }
        if (verticalCoordinatesList.size()==0){
            Log.v(TAG,"VerticalCoordinatesList的size不能为0");
            return;
        }
        if (horizontalCoordinatesList==null){
            Log.v(TAG,"请初始化纵坐标刻度值，setHorizontalCoordinatesList");
            return;
        }
        if (horizontalCoordinatesList.size()==0){
            Log.v(TAG,"horizontalCoordinatesList的size不能为0");
            return;
        }
        if (scrollerPointModelList==null){
            Log.v(TAG,"请初始化所有坐标值，setScrollerPointModelList");
            return;
        }
        if (scrollerPointModelList.size()==0){
            Log.v(TAG,"scrollerPointModelList的size不能为0");
            return;
        }

        /**先计算原点的位置*/
        originalPoint=calculateOriginalPoint();
        horizontalAverageWidth=(getWidth()-getPaddingRight()-originalPoint.x)*horizontalRatio;
        verticalRegionLength=originalPoint.y-getPaddingTop()-((originalPoint.y-getPaddingTop())/verticalCoordinatesList.size());

        /**画横坐标的线,如果可以滑动，默认横坐标的线要画满整个view的宽度，因为数据多，能表示出滑动还有数据，如果不可以滑动，则默认宽度不占满，并且留出一个横坐标平均区间的50%，看起来美观*/
        if (isScoll){
            canvas.drawLine((float) originalPoint.x+getScrollX(),(float) originalPoint.y,(float) getWidth()-getPaddingRight()+getScrollX(),(float) originalPoint.y,horizontalLinePaint);
        }else {
            canvas.drawLine((float) originalPoint.x,(float) originalPoint.y,(float) (getWidth()-getPaddingRight()-((getWidth()-getPaddingRight()-originalPoint.x)/(horizontalCoordinatesList.size()+1)/4)),(float) originalPoint.y,horizontalLinePaint);
        }
        /**画纵坐标的线*/
        canvas.drawLine((float) originalPoint.x+getScrollX(),(float) originalPoint.y,(float) originalPoint.x+getScrollX(),(float) getPaddingTop()+((originalPoint.y-getPaddingTop())/verticalCoordinatesList.size())-verticalTextPaint.getTextSize()/2,verticalLinePaint);
        /**画纵坐标的刻度值*/
        drawVerticalLineCoordinates(canvas,originalPoint);
        /**画横坐标的刻度值*/
        drawHorizontalLineCoordinates(canvas,originalPoint);
        /**画所有的点*/
        drawAllPoint(canvas,originalPoint,horizontalAverageWidth,verticalRegionLength);
    }

    private void drawAllPoint(Canvas canvas, Point point, float horizontalAverageWidth, float verticalRegionLength) {
        Path path=new Path();
        for (int i=0;i<scrollerPointModelList.size();i++ ){
            float x=((scrollerPointModelList.get(i).getX()-horizontalMin)/horizontalAverageWeight*horizontalAverageWidth)+point.x;
            float y=point.y-((scrollerPointModelList.get(i).getY()-verticalMin)/(verticalMax-verticalMin)* verticalRegionLength);
            pointTextPaint.setColor(Color.BLUE);
            pointTextPaint.setStrokeWidth(5);
            pointTextPaint.setStyle(Paint.Style.STROKE);
            if (i==0){
                path.moveTo(x,y);
            }else {
                path.lineTo(x,y);
            }
            canvas.drawPath(path,pointTextPaint);
        }
        for (int i=0;i<scrollerPointModelList.size();i++ ){
            float x=((scrollerPointModelList.get(i).getX()-horizontalMin)/horizontalAverageWeight*horizontalAverageWidth)+point.x;
            float y=point.y-((scrollerPointModelList.get(i).getY()-verticalMin)/(verticalMax-verticalMin)* verticalRegionLength);
            pointTextPaint.setColor(Color.RED);
            pointTextPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(x,
                    y,10,pointTextPaint);
        }
    }

    /**画纵坐标的刻度值*/
    private void drawVerticalLineCoordinates(Canvas canvas, Point point) {
                for (int i=0;i<verticalCoordinatesList.size();i++){
                    canvas.drawText(verticalCoordinatesList.get(i),point.x+getScrollX()-((int) verticalTextoffsetX),
                            point.y-(point.y-getPaddingTop())/verticalCoordinatesList.size()*i-getTextOffset(verticalTextPaint,verticalCoordinatesList.get(i)),
                            verticalTextPaint);
        }
    }


    /**画横坐标的刻度值*/

    private void drawHorizontalLineCoordinates(Canvas canvas,Point point) {
        /** 默认横坐标文字两边需要留白，避免相邻的横坐标值挨在一起，所以在现有的宽度上减少一点*/
        if (!isScoll){
            /** 如果不可以滑动，横坐标平均区间由horizontalCoordinatesList.size()来决定*/
            horizontalRatio=1f/(float) (horizontalCoordinatesList.size()+1);
        }
        float HorizontalAverageTextwidth=(getWidth()-getPaddingRight()-point.x)*horizontalRatio*4/5;
        Rect horizontalRectOneText=getTextRect(horizontalTextPaint,horizontalCoordinatesList.get(0));
        for (int i=0;i<horizontalCoordinatesList.size();i++){
                    StaticLayout sl = new StaticLayout(horizontalCoordinatesList.get(i),horizontalTextPaint,(int)HorizontalAverageTextwidth, Layout.Alignment.ALIGN_NORMAL,1.0f,0.0f,true);
                    canvas.save();
                    canvas.translate((float) point.x+(getWidth()-getPaddingRight()-point.x)*horizontalRatio*(i+1)-(sl.getLineWidth(0)/2),(float) point.y+(horizontalRectOneText.bottom-horizontalRectOneText.top));
                    sl.draw(canvas);
                    canvas.translate(0,0);
                    canvas.restore();

        }
    }

    /**先计算原点的位置*/
    private Point calculateOriginalPoint() {
        Point point=new Point();
        /**计算出原点需要向上偏移多少*/
        if (!isScoll){
            /** 如果不可以滑动，横坐标平均区间由horizontalCoordinatesList.size()来决定*/
            horizontalRatio=1f/(float) (horizontalCoordinatesList.size()+1);
        }
        // 默认横坐标文字两边需要留白，避免相邻的横坐标值挨在一起，所以在现有的宽度上减少一点
        float HorizontalAverageTextwidth=(getWidth()-getPaddingRight()-point.x)*horizontalRatio*4/5;
        //拿到横坐标在换行后高度最大的高度
        point.y=0;
        for (int i=0;i<horizontalCoordinatesList.size();i++){
            StaticLayout sl = new StaticLayout(horizontalCoordinatesList.get(i),horizontalTextPaint,(int)HorizontalAverageTextwidth, Layout.Alignment.ALIGN_NORMAL,1.0f,0.0f,true);
            if (sl.getHeight()>point.y){
                point.y=sl.getHeight();
            }
        }
        //默认横坐标文字的上下需要留白，所以这个高度要加大,上下留出一个字符大小的距离
        Rect horizontalRectOneText=getTextRect(horizontalTextPaint,horizontalCoordinatesList.get(0));
        point.y=point.y+(horizontalRectOneText.bottom-horizontalRectOneText.top)*2;
        //矫正至Canvas所在的坐标
        point.y=getHeight()-getPaddingBottom()-point.y;

        //这里要修正纵坐标刻度值的textSize，因为防止数据过多之后产生上下重叠现象
        int verticalAverage=(point.y-getPaddingTop())/verticalCoordinatesList.size();
        if (verticalTextPaint.getTextSize()>=verticalAverage){
            verticalTextPaint.setTextSize(verticalAverage*3/4);
        }
        /**计算出原点需要向右偏移多少*/
        //拿到纵坐标长度最长的字符串
        String verticalMaxLengthString="";
        for (int i=0;i<verticalCoordinatesList.size();i++){
            if (verticalCoordinatesList.get(i).length()>verticalMaxLengthString.length()){
                verticalMaxLengthString=verticalCoordinatesList.get(i);
            }
        }
        // 默认纵坐标文字两边需要留白，所以增加一点,默认增加文字长度的一半
        Rect rect=getTextRect(verticalTextPaint,verticalMaxLengthString);
        verticalTextoffsetX=(rect.bottom-rect.top)*2;
        point.x=(int) ((rect.right-rect.left)+(rect.bottom-rect.top)*4+getPaddingLeft());
        return point;
    }
    public Rect getTextRect(Paint paint, String text){
        Rect bounds=new Rect();
        paint.getTextBounds(text,0,text.length(),bounds);
        return bounds;
    }
    public float getTextOffset(Paint paint, String text){
        Rect bounds=new Rect();
        paint.getTextBounds(text,0,text.length(),bounds);
        float offset=(bounds.top+bounds.bottom)/2;
        return offset;
    }
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int x = (int) event.getX();
        int y = (int) event.getY();
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX=x;
                downY=y;
                getParent().requestDisallowInterceptTouchEvent(true);
                if (!scroller.isFinished()) {
                    isFling=false;
                    scroller.abortAnimation();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                int dx = x - mLastX;
                int scrollX=x-downX;
                int scrollY=y-downY;
                if(Math.abs(scrollX)<Math.abs(scrollY)){
                    if (getScrollX()<0){
                        scroller.startScroll(getScrollX(),0,-getScrollX(),0,800);
                        invalidate();
                    }else if (getScrollX()>=(scrollerPointModelList.size()*horizontalAverageWidth-((getWidth()-getPaddingRight()-originalPoint.x)))){
                        scroller.startScroll(getScrollX(),0,(int) (scrollerPointModelList.size()*horizontalAverageWidth-(getWidth()-getPaddingRight()-originalPoint.x)-getScrollX()),0,800);
                        invalidate();
                    }
                    getParent().requestDisallowInterceptTouchEvent(false);
                }else{
                    getParent().requestDisallowInterceptTouchEvent(true);
                    if (getScrollX()<0||getScrollX()>=(scrollerPointModelList.size()*horizontalAverageWidth-((getWidth()-getPaddingRight()-originalPoint.x)))){
                        dx=dx/2;
                    }
                    scrollBy(-dx, 0);
                    invalidate();
                }

                break;
            case MotionEvent.ACTION_UP:
                if (getScrollX()<0){
                    scroller.startScroll(getScrollX(),0,-getScrollX(),0,800);
                    invalidate();
                }else if (getScrollX()>=(scrollerPointModelList.size()*horizontalAverageWidth-((getWidth()-getPaddingRight()-originalPoint.x)))){
                    scroller.startScroll(getScrollX(),0,(int) (scrollerPointModelList.size()*horizontalAverageWidth-(getWidth()-getPaddingRight()-originalPoint.x)-getScrollX()),0,800);
                    invalidate();
               }else{
                    final int pointerId = event.getPointerId(0);
                    mVelocityTracker.computeCurrentVelocity(1000, ViewConfiguration.getMaximumFlingVelocity());
                    final int velocityX = (int)mVelocityTracker.getXVelocity(pointerId);
                    isFling=true;
                    scroller.fling(getScrollX(),0,-velocityX,0,-getWidth(),(int) (scrollerPointModelList.size()*horizontalAverageWidth)+getWidth(),0,0);
                    invalidate();
                    if (mVelocityTracker != null) {
                        mVelocityTracker.clear();
                    }
                }
                break;
        }
        mLastX = x;
        return true;
    }
    @Override
    public void computeScroll() {
        super.computeScroll();
        if (scroller.computeScrollOffset()){
            if (isFling){
                if (scroller.isFinished()){
                    if (scroller.getCurrX()<=0){
                        isFling=false;
                        scroller.startScroll(getScrollX(),0,-getScrollX(),0,800);
                        invalidate();
                    }else if (scroller.getCurrX()>=(scrollerPointModelList.size()*horizontalAverageWidth-((getWidth()-getPaddingRight()-originalPoint.x)))){
                        isFling=false;
                        scroller.startScroll(getScrollX(),0,(int) (scrollerPointModelList.size()*horizontalAverageWidth-(getWidth()-getPaddingRight()-originalPoint.x)-getScrollX()),0,800);
                        invalidate();
                    }
                }else {
                    if  (scroller.getCurrX()<=-(getWidth()-getPaddingRight()-originalPoint.x)/2){
                        scroller.abortAnimation();
                        isFling=false;
                        scroller.startScroll(getScrollX(),0,-getScrollX(),0,800);
                        invalidate();
                    }else if (scroller.getCurrX()>=(scrollerPointModelList.size()*horizontalAverageWidth-((getWidth()-getPaddingRight()-originalPoint.x)))){
                        scroller.abortAnimation();
                        isFling=false;
                        scroller.startScroll(getScrollX(),0,(int) (scrollerPointModelList.size()*horizontalAverageWidth-(getWidth()-getPaddingRight()-originalPoint.x)-getScrollX()),0,800);
                        invalidate();
                    }else {
                        scrollTo(scroller.getCurrX(), scroller.getCurrY());
                        invalidate();
                    }
                }
            }else {
                scrollTo(scroller.getCurrX(), scroller.getCurrY());
                invalidate();
            }

        }
    }
    public Paint getHorizontalLinePaint() {
        return horizontalLinePaint;
    }
    public Paint getVerticalLinePaint() {
        return verticalLinePaint;
    }
    public TextPaint getHorizontalTextPaint() {
        return horizontalTextPaint;
    }
    public TextPaint getVerticalTextPaint() {
        return verticalTextPaint;
    }

    public TextPaint getPointTextPaint() {
        return pointTextPaint;
    }
    public boolean isScoll() {
        return isScoll;
    }
    public void setScoll(boolean scoll) {
        isScoll = scoll;
    }
    //设置纵坐标最小值和最大值区间
    public void setVerticalMinAndMax(float verticalMin,float verticalMax) {
        this.verticalMin=verticalMin;
        this.verticalMax=verticalMax;
        invalidate();
    }
    //设置横坐标最小值以及每个平均区间所占值
    public void setHorizontalMinAndAverageWeight(float horizontalMin,float horizontalAverageWeight) {
        this.horizontalMin=horizontalMin;
        this.horizontalAverageWeight=horizontalAverageWeight;
        invalidate();
    }
    public float getHorizontalRatio() {
        return horizontalRatio;
    }

    public void setHorizontalRatio(float horizontalRatio) {
        this.horizontalRatio = horizontalRatio;
    }
    public void setHorizontalCoordinatesListScroll(List<String> horizontalCoordinatesList,float horizontalRatio) {
        this.horizontalCoordinatesList = horizontalCoordinatesList;
        this.isScoll=true;
        this.horizontalRatio=horizontalRatio;
        invalidate();
    }
    public void setHorizontalCoordinatesListNoScroll(List<String> horizontalCoordinatesList) {
        this.horizontalCoordinatesList = horizontalCoordinatesList;
        this.isScoll=false;
        invalidate();
    }
    public void setVerticalCoordinatesList(List<String> verticalCoordinatesList) {
        this.verticalCoordinatesList = verticalCoordinatesList;
        invalidate();
    }
    public List<? extends ScrollerPointModel> getScrollerPointModelList() {
        return scrollerPointModelList;
    }

    public void setScrollerPointModelList(List<? extends ScrollerPointModel> scrollerPointModelList) {
        this.scrollerPointModelList = scrollerPointModelList;
        invalidate();
    }

}