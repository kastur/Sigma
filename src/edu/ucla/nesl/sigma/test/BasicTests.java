package edu.ucla.nesl.sigma.test;

import android.app.Activity;
import android.os.Bundle;
import edu.ucla.nesl.sigma.base.SigmaJNI;
import edu.ucla.nesl.sigma.samples.BunchOfButtonsActivity;

public class BasicTests extends BunchOfButtonsActivity {

    @Override
    public void onCreateHook() {
        addButton("socketpair() IO", new Runnable() {
            @Override
            public void run() {
                SigmaJNI.testLocalReadWrite();
            }
        });
    }
}