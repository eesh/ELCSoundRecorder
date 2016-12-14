package elcapps.elcasoundrecorder;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Eesh on 22/06/16.
 */
public class DataModel {

    private static List<RecordedFile> recordings = new ArrayList<RecordedFile>();
    private static DataModel instance = null;

    DataModel() {

    }


    public static DataModel getInstance() {
        if(instance == null) {
            instance = new DataModel();
        }
        return instance;
    }

    public static List<RecordedFile> getRecordedFiles() {
        return recordings;
    }

    public static void updateList(List<RecordedFile> files) {

        recordings = new ArrayList<>(files);
    }

}
