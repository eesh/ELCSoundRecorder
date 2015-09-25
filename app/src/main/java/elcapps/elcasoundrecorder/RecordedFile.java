package elcapps.elcasoundrecorder;

public class RecordedFile {

    protected String filename = "Recording";
    protected String duration = "00:00";
    protected String date = "1/1/1";
    protected String path = "";
    protected String time = "";
    protected boolean selected = false;

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public RecordedFile() {
        super();
    }

    public RecordedFile(String fname, String dura,String d, String t, String p) {
        super();
        this.filename = fname;
        this.path = p;
        this.time = t;
        if(d.charAt(2) != '/') {
            this.date = dateconverstion(d);
        } else {
            this.date = d;
        }
        long dur = Long.parseLong(dura);
        String seconds = String.valueOf((dur % 60000) / 1000);
        String minutes = String.valueOf(dur / 60000);
        if (seconds.length() == 1) {
            duration = "0" + minutes + ":0" + seconds;
        }else {
            duration = "0" + minutes + ":" + seconds;
        }
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    private String dateconverstion(String input) {
        if(input == null) return  "";
        String year="",month="",date="",str="";
        for(int i=0;i < 8;i++) {
            if(i < 4) {
                year = year.concat(Character.toString(input.charAt(i)));
            } else if( i >=4 && i < 6) {
                month = month.concat(Character.toString(input.charAt(i)));
            } else {
                date = date.concat(Character.toString(input.charAt(i)));
            }
        }
        str = date + "/" + month + "/" + year;
        return str;
    }
}
