package io.github.muntashirakon.AppManager.main;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.adapters.ArchivedAppsAdapter;
import io.github.muntashirakon.AppManager.db.entity.ArchivedApp;
import io.github.muntashirakon.AppManager.viewmodel.ArchivedAppsViewModel;

public class ArchivedAppsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_archived_apps);

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        ArchivedAppsViewModel viewModel = new ViewModelProvider(this).get(ArchivedAppsViewModel.class);
        viewModel.getArchivedApps().observe(this, archivedApps -> {
            ArchivedAppsAdapter adapter = new ArchivedAppsAdapter(archivedApps, this::onRestoreClicked);
            recyclerView.setAdapter(adapter);
        });
    }

    private void onRestoreClicked(ArchivedApp archivedApp) {
        if (archivedApp.apkPath != null) {
            io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat installer = io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.getNewInstance();
            installer.install(new io.github.muntashirakon.io.Path[]{io.github.muntashirakon.io.Paths.get(archivedApp.apkPath)}, null, null);
        } else {
            // Fallback to the old method if the apkPath is not available
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + archivedApp.packageName));
                startActivity(intent);
            } catch (android.content.ActivityNotFoundException anfe) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + archivedApp.packageName)));
            }
        }
    }
}