package erikterwiel.easydoorapp;

import android.Manifest;
import android.content.Intent;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class ResidentListActivity extends AppCompatActivity {

    private MenuItem mAddButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resident_list);
        ActivityCompat.requestPermissions(
                this,
                new String[] {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE},
                101);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_resident_add, menu);

        mAddButton = menu.findItem(R.id.list_add_resident_button);
        mAddButton.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                Intent addIntent =
                        new Intent(ResidentListActivity.this, ResidentAddActivity.class);
                startActivity(addIntent);
                return false;
            }
        });

        return super.onCreateOptionsMenu(menu);
    }
}
