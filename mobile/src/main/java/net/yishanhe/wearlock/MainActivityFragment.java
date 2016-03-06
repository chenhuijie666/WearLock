package net.yishanhe.wearlock;

import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.text.Editable;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import net.yishanhe.wearcomm.events.ReceiveMessageEvent;
import net.yishanhe.wearlock.events.MessageEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jboss.aerogear.security.otp.Totp;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnTextChanged;

/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragment extends Fragment {

    public static final String TAG = "MainActivityFragment";

    @Bind(R.id.wearlock_status) TextView status;
    @Bind(R.id.edit_input_pin) EditText pinEditText;
    @Bind(R.id.input_pin_binary) TextView pinBinaryText;
    @Bind(R.id.received_pin_binary) TextView receivedBinaryText;
    private int count = 0;
    private int cumulativeRight = 0;
    private int cumulativeWrong = 0;
    private double cumulativeBER = 0.0;

    @OnTextChanged(value = R.id.edit_input_pin, callback = OnTextChanged.Callback.AFTER_TEXT_CHANGED)
    public void afterPinInputChanged(Editable s) {
        if (s.length() == 6) {
            totp = s.toString();
            Log.d(TAG, "afterPinInputChanged: get totp "+totp);
            totpBinary="";
            for (int i = 0; i < totp.length(); i++) {
                String tmp = Integer.toBinaryString(Integer.valueOf(totp.substring(i,i+1)));
                while(tmp.length() < 4){
                    tmp = "0" + tmp;
                }
                totpBinary+=tmp;
            }
            Log.d(TAG, "afterPinInputChanged: get totp binary "+totpBinary);
            pinBinaryText.setText(totpBinary);
            EventBus.getDefault().post(new ReceiveMessageEvent("/PIN",totpBinary.getBytes()));
        }
    }

    @Bind(R.id.btn_rand_pin) Button genRndPinBtn;

    private String totp;
    private String totpBinary;

    @OnClick(R.id.btn_rand_pin)
    public void genRandomPin() {
        String secret = "B2374TNIQ3HKC446";
        // initialize OTP
        Totp generator = new Totp(secret);
        // generate token
        totp = generator.now();
        pinEditText.setText(totp);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_main, container, false);

        ButterKnife.bind(this, v);

        status.setMovementMethod(new ScrollingMovementMethod());
        status.setText("");
        return v;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MessageEvent event){
        if (event.getPath().equalsIgnoreCase("/clean_status")) {
            status.setText("");
            pinEditText.setText("");
            totpBinary = "";
            totp = "";
        }

        if (event.getPath().equalsIgnoreCase("/demodulated_result")) {
            receivedBinaryText.setText(event.getMessage());
            // update BER
            count += 1;
            String received = receivedBinaryText.getText().toString();
            String sent = pinBinaryText.getText().toString();
            double ber = 0.0;
            int right = 0;
            int wrong = 0;
            for (int i = 0; i < received.length(); i++) {
               if (received.charAt(i) == sent.charAt(i)) {
                   right +=1;
               } else {
                   wrong += 1;
               }
            }
            ber = ((double)wrong)/received.length();
            cumulativeRight += right;
            cumulativeWrong += wrong;
            cumulativeBER = ((double)cumulativeWrong)/(cumulativeWrong+cumulativeRight);
            status.append("BER:"+String.format("%.4f",ber)+", cumulative BER:"+String.format("%.4f",cumulativeBER)+"\n");

        }

        if (event.getPath().equalsIgnoreCase("/fixed_input")) {
            pinBinaryText.setText(event.getMessage());
        }

        if (event.getPath().equalsIgnoreCase("/UPDATE_STATUS")) {
            status.append(event.getTag()+": "+event.getMessage()+"\n");
        }

    }




}
