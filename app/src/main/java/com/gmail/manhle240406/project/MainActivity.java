package com.gmail.manhle240406.project;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.*;
import androidx.fragment.app.*;
import com.gmail.manhle240406.project.auth.AuthActivity;
import com.gmail.manhle240406.project.home.HomeFragment;
import com.gmail.manhle240406.project.wiring.MainPageFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/** One window, one navigation bar and lifecycle-owned content destinations. */
public class MainActivity extends AppCompatActivity {
    public static final String PAGE="main_page";
    private BottomNavigationView bottomNav;
    private String destination="home", notificationOrigin="home";
    private boolean selecting;
    private String conversationId;
    private String toolsOrigin="home",discoveryOrigin="home";
    private Bundle discoveryArgs=new Bundle();
    public void discovery(String search,boolean filters){discoveryArgs=new Bundle();discoveryArgs.putString("search",search);discoveryArgs.putBoolean("filters",filters);Fragment old=getSupportFragmentManager().findFragmentByTag("discovery");if(old!=null)getSupportFragmentManager().beginTransaction().remove(old).commitNow();navigate("discovery");}
    private com.gmail.manhle240406.project.profile.ProfileSetupViewModel profileGate;
    private String requestedPage;
    private boolean profileReady,setupOpening;
    private android.widget.LinearLayout readiness;
    private android.widget.TextView readinessText;
    private com.google.android.material.button.MaterialButton readinessRetry;
    public static void open(Activity source,String page){
        source.startActivity(new Intent(source,MainActivity.class).putExtra(PAGE,page)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }
    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);EdgeToEdge.enable(this);setContentView(R.layout.activity_main);
        bottomNav=findViewById(R.id.bottom_navigation);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root),(v,i)->{
            Insets bars=i.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left,bars.top,bars.right,0);
            bottomNav.setVisibility(!profileReady||i.isVisible(WindowInsetsCompat.Type.ime())?View.GONE:View.VISIBLE);
            return new WindowInsetsCompat.Builder(i).setInsets(WindowInsetsCompat.Type.statusBars(),Insets.NONE).setInsets(WindowInsetsCompat.Type.displayCutout(),Insets.NONE).build();
        });
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_fragment_container),(v,i)->{
            v.setPadding(0,0,0,i.getInsets(WindowInsetsCompat.Type.ime()).bottom);
            return new WindowInsetsCompat.Builder(i).setInsets(WindowInsetsCompat.Type.navigationBars(),Insets.NONE).setInsets(WindowInsetsCompat.Type.ime(),Insets.NONE).build();
        });
        ((AniloveApplication)getApplication()).auth().state().observe(this,state->{
            if(state==null||state.uid==null){startActivity(new Intent(this,AuthActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();}
            else if(profileGate!=null&&state.setupReady&&!state.pending&&!profileGate.checked&&!profileGate.busy&&profileGate.code==com.gmail.manhle240406.project.profile.ProfileRepository.Code.OK)profileGate.load();
        });
        if(saved!=null){destination=saved.getString(PAGE,"home");notificationOrigin=saved.getString("notification_origin","home");toolsOrigin=saved.getString("tools_origin","home");discoveryOrigin=saved.getString("discovery_origin","home");discoveryArgs=saved.getBundle("discovery_args")==null?new Bundle():saved.getBundle("discovery_args");conversationId=saved.getString("conversation_id");}
        bottomNav.setOnItemSelectedListener(item->{if(selecting)return true;navigate(item.getItemId()==R.id.nav_favorites?"favorites":item.getItemId()==R.id.nav_chat?"chat":item.getItemId()==R.id.nav_profile?"profile":"home");return false;});
        bottomNav.setOnItemReselectedListener(item->{});
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){public void handleOnBackPressed(){
            if("home".equals(destination)){setEnabled(false);getOnBackPressedDispatcher().onBackPressed();setEnabled(true);}
            else navigate(parentPage());
        }});
        requestedPage=saved!=null?destination:getIntent().getStringExtra(PAGE)==null?destination:getIntent().getStringExtra(PAGE);
        readiness=com.gmail.manhle240406.project.community.SocialViews.column(this);
        readiness.setGravity(android.view.Gravity.CENTER);
        android.widget.ProgressBar startupProgress=new android.widget.ProgressBar(this);startupProgress.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);readiness.addView(startupProgress);
        readinessText=com.gmail.manhle240406.project.community.SocialSurface.muted(this,getString(R.string.profile_loading));readiness.addView(readinessText);
        readinessRetry=com.gmail.manhle240406.project.community.SocialSurface.quiet(this,getString(R.string.wiring_retry),()->{((AniloveApplication)getApplication()).auth().refreshReadiness();profileGate.load();});readiness.addView(readinessRetry);readinessRetry.setVisibility(View.GONE);
        ((android.widget.FrameLayout)findViewById(R.id.main_fragment_container)).addView(readiness,new android.widget.FrameLayout.LayoutParams(-1,-1));
        bottomNav.setVisibility(View.GONE);
        profileGate=new androidx.lifecycle.ViewModelProvider(this).get(com.gmail.manhle240406.project.profile.ProfileSetupViewModel.class);
        profileGate.changes.observe(this,ignored->{
            if(profileGate.checked&&!profileGate.ready&&!setupOpening){setupOpening=true;startActivity(new Intent(this,com.gmail.manhle240406.project.profile.ProfileSetupActivity.class).putExtra(PAGE,requestedPage));finish();}
            else if(profileGate.ready&&!profileReady){profileReady=true;((android.view.ViewGroup)readiness.getParent()).removeView(readiness);bottomNav.setVisibility(View.VISIBLE);navigate(requestedPage);}
            else if(!profileReady){readinessText.setText(profileGate.code==com.gmail.manhle240406.project.profile.ProfileRepository.Code.OK?R.string.profile_loading:R.string.setup_load_failed);readinessRetry.setVisibility(profileGate.busy?View.GONE:View.VISIBLE);}
        });
        com.gmail.manhle240406.project.auth.AuthState auth=((AniloveApplication)getApplication()).auth().state().getValue();
        if(auth!=null&&auth.setupReady&&!profileGate.checked&&!profileGate.busy)profileGate.load();
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);requestedPage=intent.getStringExtra(PAGE)==null?"home":intent.getStringExtra(PAGE);if(profileReady)navigate(requestedPage);}
    @Override protected void onSaveInstanceState(Bundle out){out.putString("tools_origin",toolsOrigin);out.putString("discovery_origin",discoveryOrigin);out.putBundle("discovery_args",discoveryArgs);out.putString("conversation_id",conversationId);out.putString(PAGE,destination);out.putString("notification_origin",notificationOrigin);super.onSaveInstanceState(out);}
    public void home(){navigate("home");}
    private String parentPage(){return destination.startsWith("tools:")?"tools":destination.equals("discovery")?discoveryOrigin:destination.equals("tools")?toolsOrigin:destination.startsWith("conversation:")?"chat":destination.equals("notifications")?notificationOrigin:"home";}
    public void up(){navigate(parentPage());}
    public void conversation(String id){if(profileReady&&com.gmail.manhle240406.project.profile.ProfileSchema.validOperationId(id)){conversationId=id;show("conversation:"+id);}}
    public void navigate(String requested){
        if(isFinishing()||isDestroyed())return;
        if(!profileReady){requestedPage=requested;return;}
        if(requested!=null&&requested.startsWith("conversation:")&&com.gmail.manhle240406.project.profile.ProfileSchema.validOperationId(conversationId)){show("conversation:"+conversationId);return;}
        final String page=java.util.Arrays.asList("home","favorites","chat","profile","notifications","discovery","tools","tools:0","tools:1","tools:2","tools:3","tools:4","tools:5").contains(requested)?requested:"home";
        Fragment current=getSupportFragmentManager().getPrimaryNavigationFragment();
        Runnable change=()->show(page);
        if(!page.equals(destination)&&current instanceof MainPageFragment)((MainPageFragment)current).leave(change);else change.run();
    }
    private void show(String page){
        FragmentManager fm=getSupportFragmentManager();if(fm.isStateSaved())return;
        if(!page.equals(destination)&&getCurrentFocus()!=null){View focus=getCurrentFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(),0);focus.clearFocus();}
        if(page.equals("notifications")&&!destination.equals("notifications"))notificationOrigin=destination;
        if(page.equals("tools")&&!destination.equals(page)&&!destination.startsWith("tools:")&&!destination.equals("discovery"))toolsOrigin=destination;
        if(page.equals("discovery")&&!destination.equals(page))discoveryOrigin=destination;
        Fragment next=fm.findFragmentByTag(page);
        FragmentTransaction tx=fm.beginTransaction().setReorderingAllowed(true);
        for(Fragment f:fm.getFragments())if(f.isAdded()){
            if(f instanceof com.gmail.manhle240406.project.community.ConversationFragment && f!=next)tx.remove(f);
            else tx.hide(f).setMaxLifecycle(f,androidx.lifecycle.Lifecycle.State.STARTED);
        }
        if(next==null){
            next=page.equals("discovery")?new com.gmail.manhle240406.project.adoption.DiscoveryFragment():page.startsWith("tools")?new com.gmail.manhle240406.project.wiring.FlowHubFragment():page.startsWith("conversation:")?com.gmail.manhle240406.project.community.ConversationFragment.create(conversationId):page.equals("favorites")?new com.gmail.manhle240406.project.adoption.FavoritesLibraryFragment()
                :page.equals("profile")?new com.gmail.manhle240406.project.profile.ProfileFragment()
                :page.equals("notifications")?new com.gmail.manhle240406.project.notifications.InboxFragment()
                :page.equals("chat")?new com.gmail.manhle240406.project.community.ConversationsFragment():new HomeFragment();
            if(page.equals("discovery"))next.setArguments(discoveryArgs);
            if(page.startsWith("tools:")){Bundle args=new Bundle();args.putInt("group",Integer.parseInt(page.substring(6)));next.setArguments(args);}
            tx.add(R.id.main_fragment_container,next,page);
        }else tx.show(next);
        tx.setMaxLifecycle(next,androidx.lifecycle.Lifecycle.State.RESUMED).setPrimaryNavigationFragment(next).commitNow();
        destination=page;selecting=true;
        String selected=page.startsWith("tools")?toolsOrigin:page.equals("discovery")?(discoveryOrigin.startsWith("tools")?toolsOrigin:discoveryOrigin):page;
        bottomNav.setSelectedItemId(selected.equals("favorites")?R.id.nav_favorites:(selected.equals("chat")||selected.startsWith("conversation:"))?R.id.nav_chat:selected.equals("profile")?R.id.nav_profile:R.id.nav_home);
        selecting=false;
    }
    public String destination(){return destination;}
}
