package elcapps.elcasoundrecorder;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;


public class ColorPicker extends DialogFragment {

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.Theme_AppCompat_Light_Dialog_Alert);
        builder.setTitle("Pick a color");
        final CharSequence[] colors = { "Red", "Pink", "Orange", "Yellow"
                , "Blue", "Light Blue", "Teal", "Cyan"
                , "Green", "Light Green", "Lime", "Purple", "Deep Purple"
                , "Amber", "Grey", "Blue Grey", "AMOLED" };
        builder.setItems(colors, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                ((main)getActivity()).setColor(i);
            }
        });
        builder.setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        return builder.create();
    }
}