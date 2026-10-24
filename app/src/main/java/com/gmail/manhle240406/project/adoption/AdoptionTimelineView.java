package com.gmail.manhle240406.project.adoption;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.SocialViews;
import com.google.android.material.card.MaterialCardView;
import java.util.Collections;
import java.util.List;

/** Expandable native vertical stepper for adoption lifecycle states. */
public final class AdoptionTimelineView extends LinearLayout {
 public enum State{COMPLETE,CURRENT,UPCOMING}
 public static final class Step{public final CharSequence title,detail;public final State state;public Step(CharSequence title,CharSequence detail,State state){this.title=title;this.detail=detail;this.state=state;}}
 public AdoptionTimelineView(@NonNull Context context){super(context);setTag("adoption_timeline");setOrientation(VERTICAL);setClipChildren(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
 public void setSteps(List<Step> value){removeAllViews();List<Step> steps=value==null?Collections.emptyList():value;for(int i=0;i<steps.size();i++)addStep(steps.get(i),i==0,i==steps.size()-1);}
 private void addStep(Step step,boolean first,boolean last){
  LinearLayout row=new LinearLayout(getContext());row.setOrientation(HORIZONTAL);row.setGravity(Gravity.TOP);row.setClipChildren(false);
  row.addView(new Rail(getContext(),step.state,first,last),new LinearLayout.LayoutParams(dp(44),-1));
  MaterialCardView card=new MaterialCardView(getContext());card.setTag("adoption_timeline_"+step.state.name().toLowerCase(java.util.Locale.ROOT));card.setRadius(dp(16));card.setCardElevation(0);card.setUseCompatPadding(false);card.setStrokeWidth(dp(step.state==State.CURRENT?2:1));card.setStrokeColor(color(step.state==State.CURRENT?R.color.adoption_timeline_current_border:R.color.anilove_border));card.setCardBackgroundColor(color(step.state==State.CURRENT?R.color.adoption_timeline_current_bg:R.color.anilove_card_bg));
  LinearLayout content=new LinearLayout(getContext());content.setOrientation(VERTICAL);content.setPadding(dp(16),dp(14),dp(16),dp(14));
  content.addView(SocialViews.text(getContext(),step.title.toString(),true),new LinearLayout.LayoutParams(-1,-2));
  TextView detail=SocialViews.text(getContext(),step.detail.toString(),false);detail.setTextColor(color(R.color.anilove_text_muted));LinearLayout.LayoutParams detailParams=new LinearLayout.LayoutParams(-1,-2);detailParams.topMargin=dp(6);content.addView(detail,detailParams);
  if(step.state!=State.UPCOMING){boolean done=step.state==State.COMPLETE;TextView badge=SocialViews.text(getContext(),getContext().getString(done?R.string.lc_stage_done:R.string.lc_stage_current),true);badge.setTextColor(color(done?R.color.adoption_timeline_done_text:R.color.adoption_timeline_current_badge_text));badge.setGravity(Gravity.CENTER);badge.setMinHeight(dp(32));badge.setPadding(dp(12),dp(5),dp(12),dp(5));GradientDrawable bg=new GradientDrawable();bg.setColor(color(done?R.color.adoption_timeline_done_bg:R.color.adoption_timeline_current_border));bg.setCornerRadius(dp(999));badge.setBackground(bg);LinearLayout.LayoutParams badgeParams=new LinearLayout.LayoutParams(-2,-2);badgeParams.topMargin=dp(10);content.addView(badge,badgeParams);}
  card.addView(content);LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(0,-2,1);if(!last)cardParams.bottomMargin=dp(12);row.addView(card,cardParams);
  content.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);row.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);row.setContentDescription(step.title+". "+step.detail+". "+getContext().getString(step.state==State.COMPLETE?R.string.lc_stage_done:step.state==State.CURRENT?R.string.lc_stage_current:R.string.lc_stage_upcoming));addView(row,new LinearLayout.LayoutParams(-1,-2));
 }
 private int color(int id){return ContextCompat.getColor(getContext(),id);}private int dp(int value){return SocialViews.dp(getContext(),value);}
 private static final class Rail extends View{
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path check=new Path();private final State state;private final boolean first,last;private final int green,track,lime;
  Rail(Context context,State state,boolean first,boolean last){super(context);this.state=state;this.first=first;this.last=last;green=ContextCompat.getColor(context,R.color.adoption_timeline_progress);track=ContextCompat.getColor(context,R.color.adoption_timeline_track);lime=ContextCompat.getColor(context,R.color.adoption_timeline_current_border);setMinimumHeight(SocialViews.dp(context,86));setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
  @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float x=getWidth()/2f,nodeY=Math.min(getHeight()/2f,dp(30)),radius=dp(11);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(dp(4));if(!first){paint.setColor(state==State.UPCOMING?track:green);canvas.drawLine(x,0,x,nodeY-radius,paint);}if(!last){paint.setColor(state==State.COMPLETE?green:track);canvas.drawLine(x,nodeY+radius,x,getHeight(),paint);}paint.setStyle(Paint.Style.FILL);paint.setColor(state==State.UPCOMING?track:state==State.CURRENT?lime:green);canvas.drawCircle(x,nodeY,radius,paint);if(state==State.COMPLETE){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setColor(ContextCompat.getColor(getContext(),R.color.white));check.reset();check.moveTo(x-dp(5),nodeY);check.lineTo(x-dp(1),nodeY+dp(4));check.lineTo(x+dp(6),nodeY-dp(5));canvas.drawPath(check,paint);}else if(state==State.CURRENT){paint.setStyle(Paint.Style.FILL);paint.setColor(ContextCompat.getColor(getContext(),R.color.anilove_card_bg));canvas.drawCircle(x,nodeY,dp(4),paint);}paint.setStyle(Paint.Style.FILL);}
  private float dp(int value){return SocialViews.dp(getContext(),value);}
 }
}
