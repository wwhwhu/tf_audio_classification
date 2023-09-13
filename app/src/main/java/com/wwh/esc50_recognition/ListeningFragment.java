package com.wwh.esc50_recognition;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;


import com.wwh.esc50_recognition.databinding.FragmentSecondBinding;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class ListeningFragment extends Fragment {
    private Application application;

    public ListeningFragment() {
        // Required empty public constructor
    }
    private FragmentSecondBinding binding;
    private ListeningFragmentViewModel viewModel;

    private ArrayList<String> listOfPredictedClasses;

    private ArrayList<Float> listOfFloatsOfPredictedClasses;

    public static final long TIME_DELAY = 555L;

    // Update interval for widget
    public static final long UPDATE_INTERVAL_INFERENCE = 6144L;
    public static final long UPDATE_INTERVAL_KARAOKE = 440L;

    // Permissions
    int PERMISSION_ALL = 123;

    String[] PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private void lookForPermissions() {
        Log.d("Info_ESC50","检查权限中...");
        if (!hasPermissions(requireActivity(), PERMISSIONS)) {
            Log.d("Info_ESC50","无权限，申请权限中...");
            requestPermissions(PERMISSIONS, PERMISSION_ALL);
            Log.d("Info_ESC50","申请完毕");
        }
        Log.d("Info_ESC50","权限已有");
    }

    private void generateFolderToSDcard() {
        File root = new File(Environment.getExternalStorageDirectory(), "ESC50_Recognition");
        Log.d("Info_ESC50","创建/检查文件夹: " + root.getAbsolutePath());
        if (!root.exists()) {
            root.mkdirs();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 在此处编写你的视图创建逻辑
        // 使用 inflater.inflate() 方法加载 Fragment 的布局文件并返回根视图
        application = getActivity().getApplication();
        // Log.d("Info_ESC",Boolean.toString(application==null));
        try {
            viewModel = new ListeningFragmentViewModel(application);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // Inflate the layout for this fragment
        binding = FragmentSecondBinding.inflate(inflater);
        binding.setLifecycleOwner(this);
        binding.setViewModelListening(viewModel);

        // Keep screen on 屏幕常亮
        requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // 申请权限
        lookForPermissions();
        // 申请SD卡存储权限
        generateFolderToSDcard();
        setUpObservers();
        binding.buttonForListening.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (viewModel.listeningRunning) {
                    // Start animation
                    listeningStopped();
                    // Change button's color
                    binding.colorButton.setBackgroundResource(R.drawable.round_button_stop);
                } else {
                    // Start animation
                    animateListeningButton();
                    // Start collecting sound and inferring immediately
                    viewModel.setUpdateLoopListeningHandler();
                    // Change button's color
                    binding.colorButton.setBackgroundResource(R.drawable.round_button_start);
                }
            }
        });

        // Set first value to 1, 一开始5个类都是100%
        binding.seekBarProbs1.setProgress(1);
        binding.seekBarProbs2.setProgress(1);
        binding.seekBarProbs3.setProgress(1);
        binding.seekBarProbs4.setProgress(1);
        binding.seekBarProbs5.setProgress(1);

        return binding.getRoot();
    }

    private boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission)
                        != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    private void animateListeningButton() {
        android.view.animation.Animation animation =
                AnimationUtils.loadAnimation(requireActivity(), R.anim.scale_anim);
        binding.buttonAnimated.startAnimation(animation);
    }

    public void listeningStopped() {
        // Execute method to stop callbacks
        viewModel.stopAllListening();

        // Clear animation
        binding.buttonAnimated.clearAnimation();

        //Toast.makeText(activity, "Listening has stopped", Toast.LENGTH_LONG).show();
    }

    private void setUpObservers() {
        viewModel.listeningEnd.observe(
                requireActivity(),
                new Observer<Boolean>() {
                    @Override
                    public void onChanged(Boolean end) {
                        if (end) {
                            // Clear animation
                            binding.buttonAnimated.clearAnimation();
                        } else {
                            // Start animation
                            animateListeningButton();
                        }
                    }
                }
        );

        viewModel.listOfClasses.observe(
                requireActivity(),
                pair -> {
                    ArrayList<String> stringList = pair.getKey();
                    ArrayList<Float> floatList = pair.getValue();
                    listOfPredictedClasses = stringList;
                    listOfFloatsOfPredictedClasses = floatList;
                    Log.d("Info_ESC50", "listOfPredictedClasses:"+listOfPredictedClasses.toString());
                    Log.d("Info_ESC50", "listOfFloatsOfPredictedClasses:"+listOfFloatsOfPredictedClasses.toString());

                    // Show results on screen
                    if (((int)Math.round(listOfFloatsOfPredictedClasses.get(0) * 100)) == 0) {
                        binding.seekBarProbs1.setProgress(1);
                    } else {
                        binding.seekBarProbs1.setProgress(
                                (int) (listOfFloatsOfPredictedClasses.get(0) * 100));
                    }
                    binding.textviewProb1.setText(
                            (int) (listOfFloatsOfPredictedClasses.get(0) * 100) + "%");
                    binding.textviewClasses1.setText(listOfPredictedClasses.get(0));

                    // Similar processing for other seek bars and text views...
                    if (((int)Math.round(listOfFloatsOfPredictedClasses.get(1) * 100)) == 0) {
                        binding.seekBarProbs2.setProgress(1);
                    } else {
                        binding.seekBarProbs2.setProgress(
                                (int) (listOfFloatsOfPredictedClasses.get(1) * 100));
                    }
                    binding.textviewProb2.setText(
                            (int) (listOfFloatsOfPredictedClasses.get(1) * 100) + "%");
                    binding.textviewClasses2.setText(listOfPredictedClasses.get(1));

                    if (((int)Math.round(listOfFloatsOfPredictedClasses.get(2) * 100)) == 0) {
                        binding.seekBarProbs3.setProgress(1);
                    } else {
                        binding.seekBarProbs3.setProgress(
                                (int) (listOfFloatsOfPredictedClasses.get(2) * 100));
                    }
                    binding.textviewProb3.setText(
                            (int) (listOfFloatsOfPredictedClasses.get(2) * 100) + "%");
                    binding.textviewClasses3.setText(listOfPredictedClasses.get(2));

                    if (((int)Math.round(listOfFloatsOfPredictedClasses.get(3) * 100)) == 0) {
                        binding.seekBarProbs4.setProgress(1);
                    } else {
                        binding.seekBarProbs4.setProgress(
                                (int) (listOfFloatsOfPredictedClasses.get(3) * 100));
                    }
                    binding.textviewProb4.setText(
                            (int) (listOfFloatsOfPredictedClasses.get(3) * 100) + "%");
                    binding.textviewClasses4.setText(listOfPredictedClasses.get(3));

                    if (((int)Math.round(listOfFloatsOfPredictedClasses.get(4) * 100)) == 0) {
                        binding.seekBarProbs5.setProgress(1);
                    } else {
                        binding.seekBarProbs5.setProgress(
                                (int) (listOfFloatsOfPredictedClasses.get(4) * 100));
                    }
                    binding.textviewProb5.setText(
                            (int) (listOfFloatsOfPredictedClasses.get(4) * 100) + "%");
                    binding.textviewClasses5.setText(listOfPredictedClasses.get(4));
                }
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (viewModel.listeningRunning) {
            // Start animation
            listeningStopped();
            // Change button's color
            binding.colorButton.setBackgroundResource(R.drawable.round_button_stop);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults
    ) {
        if (requestCode == PERMISSION_ALL) {
            if (allPermissionsGranted(grantResults)) {
                Toast.makeText(
                        requireActivity(),
                        getString(R.string.allPermissionsGranted),
                        Toast.LENGTH_LONG
                ).show();
            } else {
                Toast.makeText(
                        requireActivity(),
                        getString(R.string.permissionsNotGranted),
                        Toast.LENGTH_LONG
                ).show();
                requireActivity().finish();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private boolean allPermissionsGranted(int[] grantResults) {
        for (int grantResult : grantResults) {
            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}
