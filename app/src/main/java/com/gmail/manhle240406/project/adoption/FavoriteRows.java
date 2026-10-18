package com.gmail.manhle240406.project.adoption;

import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.RecyclerView;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.SocialViews;
import com.google.firebase.firestore.DocumentSnapshot;
import java.util.*;
import java.util.function.Consumer;

/** Recycling saved rows avoids retaining a view hierarchy for every saved item. */
final class FavoriteRows extends RecyclerView.Adapter<FavoriteRows.Holder> {
 static final class Row {final String id;ListingRow source;boolean resolved;Row(String id){this.id=id;}}
 final List<Row> rows=new ArrayList<>();boolean busy,end,mutation,failed;
 private final android.content.Context context;private final Consumer<Row> open,remove;private final Runnable more;
 FavoriteRows(android.content.Context c,Consumer<Row> o,Consumer<Row> r,Runnable m){context=c;open=o;remove=r;more=m;}
 public int getItemCount(){return rows.size()+1;}public int getItemViewType(int p){return p==rows.size()?1:0;}
 public Holder onCreateViewHolder(ViewGroup parent,int type){
  LinearLayout col=SocialViews.column(context);col.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));
  if(type==1){col.addView(SocialViews.button(context,context.getString(R.string.wiring_more),more));return new Holder(col);}
  col.addView(SocialViews.text(context,"",true));col.addView(SocialViews.button(context,context.getString(R.string.wiring_open),()->{}));col.addView(SocialViews.button(context,context.getString(R.string.wiring_remove_saved),()->{}));return new Holder(col);
 }
 public void onBindViewHolder(Holder h,int p){LinearLayout col=(LinearLayout)h.itemView;
  if(p==rows.size()){h.itemView.setVisibility(rows.isEmpty()&&(busy||end&&!failed)?View.GONE:View.VISIBLE);TextView button=(TextView)col.getChildAt(0);button.setText(busy?R.string.wiring_loading:failed?R.string.wiring_retry:rows.isEmpty()?R.string.wiring_empty:end?R.string.social_end:R.string.wiring_more);button.setEnabled(!busy&&!end||failed);return;}
  Row row=rows.get(p);((TextView)col.getChildAt(0)).setText(!row.resolved?context.getString(R.string.wiring_loading):row.source==null?context.getString(R.string.wiring_unavailable_item):row.source.getString("petName"));
  View view=col.getChildAt(1);view.setEnabled(row.source!=null);view.setOnClickListener(v->open.accept(row));col.getChildAt(2).setEnabled(!mutation);col.getChildAt(2).setOnClickListener(v->remove.accept(row));
 }
 static final class Holder extends RecyclerView.ViewHolder{Holder(View v){super(v);}}
}
