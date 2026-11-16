package br.com.easyvacc.android;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ClassroomDemoTest {
    private Button button(View view, String title) {
        if (view instanceof Button && title.contentEquals(((Button) view).getText()))
            return (Button) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i=0;i<group.getChildCount();i++) {
                Button found = button(group.getChildAt(i), title);
                if (found != null) return found;
            }
        }
        return null;
    }
    @Test public void opensPanelWithFixturesAndCanSwitchBothWays() {
        try (org.robolectric.android.controller.ActivityController<MainActivity> controller =
                Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            View root = activity.getWindow().getDecorView();
            assertTrue(MainActivity.isClassroomDemo(activity));
            assertNotNull(button(root,"Abrir Portal do Cidadão"));
            assertNull(button(root,"Entrar com a conta do servidor"));
            button(root,"Abrir Portal do Cidadão").performClick();
            assertNotNull(button(root,"Consultar caderneta"));
            button(root,"Consultar caderneta").performClick();
            assertNotNull(button(root,"Voltar ao Painel Profissional"));
            button(root,"Voltar ao Painel Profissional").performClick();
            assertNotNull(button(root,"Abrir Portal do Cidadão"));
        }
        try (Db db = new Db(RuntimeEnvironment.getApplication())) {
            assertEquals(2, db.count("patients"));
            assertEquals(2, db.count("posts"));
            assertEquals(3, db.count("stock"));
            assertEquals(2, db.count("doses"));
            assertEquals(2, db.count("appointments"));
            db.prepareClassroomDemo();
            assertEquals(2, db.count("patients"));
        }
    }
    @Test public void demoCannotConnectToRealServer() {
        SyncEngine engine = new SyncEngine(RuntimeEnvironment.getApplication());
        assertFalse(engine.configured());
        assertThrows(IllegalStateException.class,
            () -> engine.connect("https://example.invalid", "demo", "never-sent"));
    }
}
