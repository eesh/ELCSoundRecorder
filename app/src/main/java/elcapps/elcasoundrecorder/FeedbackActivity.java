package elcapps.elcasoundrecorder;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.util.HashMap;
import java.util.Map;

public class FeedbackActivity extends AppCompatActivity implements FloatingActionButton.OnClickListener {


    private EditText textbox;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_feedback);
        android.support.v7.widget.Toolbar toolbar = (android.support.v7.widget.Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Feedback");
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab_submit);
        textbox = (EditText) findViewById(R.id.editText_feedback);
        fab.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        final String feedback = textbox.getText().toString();
        view.setEnabled(false);
        RequestQueue requestQueue = Volley.newRequestQueue(getApplicationContext());
        StringRequest request = new StringRequest(Request.Method.POST, "http://filtershots.com/SoundRecorder/feedback.php",
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        finish();
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("Volley", error.toString());
                finish();
            }
        }) {
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> map = new HashMap<>();
                map.put("data",feedback);
                return map;
            }
        };
        Toast.makeText(getApplicationContext(),"Sending feedback", Toast.LENGTH_LONG).show();
        requestQueue.add(request);
    }
}
