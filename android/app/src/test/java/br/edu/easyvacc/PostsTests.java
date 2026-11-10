package br.edu.easyvacc;

import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class PostsTests {
    private static Button button(View v,String label){
        if(v instanceof Button&&((TextView)v).getText().toString().equals(label))return (Button)v;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){Button b=button(g.getChildAt(i),label);if(b!=null)return b;}}
        return null;
    }
    private PostsActivity search(){
        PostsActivity page=Robolectric.buildActivity(PostsActivity.class).setup().get();
        ((EditText)page.findViewById(R.id.post_search)).setText("agua branca");
        return page;
    }
    private void preview(PostsActivity page){
        Button open=button(page.getWindow().getDecorView(),"Compartilhar endereço de ESF Água Branca");
        assertNotNull(open);open.performClick();
    }
    @Test public void searchWithoutAccentsFiltersDirectory(){
        PostsActivity page=search();
        assertNotNull(button(page.getWindow().getDecorView(),"Compartilhar endereço de ESF Água Branca"));
        assertNull(button(page.getWindow().getDecorView(),"Compartilhar endereço de ESF Bacaxá"));
    }
    @Test public void publicPreviewShowsOnlyUnitAddress(){
        preview(search());
        String message=Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).getMessage().toString();
        assertEquals("ESF Água Branca\nAvenida Nossa Senhora de Fátima, 6\nÁgua Branca · Saquarema/RJ",message);
    }
    @Test public void cancelPreviewPreservesSearchAndDoesNotShare(){
        PostsActivity page=search();preview(page);
        ShadowAlertDialog.getLatestAlertDialog().getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("agua branca",((EditText)page.findViewById(R.id.post_search)).getText().toString());
        assertNull(Shadows.shadowOf(page).getNextStartedActivity());
    }
    @Test public void chooserUsesPublicTextOnly(){
        PostsActivity page=search();preview(page);
        ShadowAlertDialog.getLatestAlertDialog().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        Intent chooser=Shadows.shadowOf(page).getNextStartedActivity();
        assertNotNull(chooser);
        assertEquals(Intent.ACTION_CHOOSER,chooser.getAction());
        Intent share=chooser.getParcelableExtra(Intent.EXTRA_INTENT);
        assertEquals(Intent.ACTION_SEND,share.getAction());
        assertEquals("text/plain",share.getType());
        assertEquals("ESF Água Branca\nAvenida Nossa Senhora de Fátima, 6\nÁgua Branca · Saquarema/RJ",share.getStringExtra(Intent.EXTRA_TEXT));
    }
}
