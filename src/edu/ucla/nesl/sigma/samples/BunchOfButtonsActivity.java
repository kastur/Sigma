package edu.ucla.nesl.sigma.samples;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;

public abstract class BunchOfButtonsActivity extends Activity {

  public static final String TAG = BunchOfButtonsActivity.class.getName();
  private LinearLayout mLayout;


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);

    mLayout = new LinearLayout(this);
    mLayout.setOrientation(LinearLayout.VERTICAL);

    ScrollView scrollView = new ScrollView(this);
    scrollView.setLayoutParams(
        new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
                                   ViewGroup.LayoutParams.WRAP_CONTENT));
    scrollView.addView(mLayout);

    LinearLayout outerLayout = new LinearLayout(this);
    outerLayout.setLayoutParams(
        new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
                                   ViewGroup.LayoutParams.FILL_PARENT));
    outerLayout.addView(scrollView);

    onCreateHook();

    setContentView(outerLayout);

  }

  public LinearLayout getLayout() {
    return mLayout;
  }

  public abstract void onCreateHook();

  public void addButton(final String text, final Runnable runnable) {
    Button button = new Button(BunchOfButtonsActivity.this);
    button.setText(text);

    if (runnable != null) {
      button.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
                    /*
                    new AsyncTask<Void, Void, Void>() {
                        @Override
                        protected Void doInBackground(Void... voids) {
                            Log.d(TAG, "CLICKED: " + text);

                            return null;
                        }
                    }.execute();
                    */
          runnable.run();
        }
      });
    }
    mLayout.addView(button);
  }
}
