package org.ea.sqrl.activites;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.ea.sqrl.R;
import org.ea.sqrl.processors.SQRLStorage;
import org.ea.sqrl.utils.EncryptionUtils;
import org.ea.sqrl.utils.Utils;

/**
 *
 * @author Daniel Persson
 */
public class SimplifiedActivity extends LoginBaseActivity {
    private static final String TAG = "SimplifiedActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simplified);

        rootView = findViewById(R.id.simplifiedActivityView);

        final IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES);
        integrator.setCameraId(0);
        integrator.setBeepEnabled(false);
        integrator.setOrientationLocked(true);
        integrator.setBarcodeImageEnabled(false);

        setupLoginPopupWindow(getLayoutInflater());
        setupLoginOptionsPopupWindow(getLayoutInflater(), true);

        setupBasePopups(getLayoutInflater(), false);


        final ImageButton btnUseIdentity = findViewById(R.id.btnUseIdentity);
        btnUseIdentity.setOnClickListener(
                v -> {
                    integrator.setPrompt(this.getString(R.string.scan_site_code));
                    integrator.initiateScan();
                }
        );


        final Button btnAdvancedFunctions = findViewById(R.id.btnAdvancedFunctions);
        btnAdvancedFunctions.setOnClickListener((v) ->
            startActivity(new Intent(this, MainActivity.class))
        );
    }


    public void setupLoginPopupWindow(LayoutInflater layoutInflater) {
        View popupView = layoutInflater.inflate(R.layout.fragment_login, null);

        loginPopupWindow = new PopupWindow(popupView,
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                true);

        final SQRLStorage storage = SQRLStorage.getInstance();

        loginPopupWindow.setTouchable(true);
        final EditText txtLoginPassword = popupView.findViewById(R.id.txtLoginPassword);

        txtLoginPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence password, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence password, int start, int before, int count) {
                if (!storage.hasQuickPass()) return;
                if ((start + count) >= storage.getHintLength()) {
                    loginPopupWindow.dismiss();
                    progressPopupWindow.showAtLocation(progressPopupWindow.getContentView(), Gravity.CENTER, 0, 0);
                    closeKeyboard();

                    new Thread(() -> {
                        boolean decryptionOk = storage.decryptIdentityKey(password.toString(), entropyHarvester, true);
                        if(!decryptionOk) {
                            Snackbar.make(rootView, getString(R.string.decrypt_identity_fail), Snackbar.LENGTH_LONG).show();
                            handler.post(() -> {
                                progressPopupWindow.dismiss();
                                loginPopupWindow.showAtLocation(loginPopupWindow.getContentView(), Gravity.CENTER, 0, 0);
                            });
                            storage.clear();
                            storage.clearQuickPass(SimplifiedActivity.this);
                            return;
                        }
                        showClearNotification();


                        try {
                            postQuery(commHandler, true, false);
                        } catch (Exception e) {
                            commHandler.clearLastResponse();
                            Log.e(TAG, e.getMessage(), e);
                            handler.post(() -> Snackbar.make(rootView, e.getMessage(), Snackbar.LENGTH_LONG).show());
                            storage.clear();
                            storage.clearQuickPass(SimplifiedActivity.this);
                            return;
                        } finally {
                            handler.post(() -> {
                                txtLoginPassword.setText("");
                                progressPopupWindow.dismiss();
                            });
                        }
                        commHandler.setAskAction(() -> {
                            handler.post(() -> {
                                progressPopupWindow.showAtLocation(progressPopupWindow.getContentView(), Gravity.CENTER, 0, 0);
                            });
                            try {
                                if(commHandler.isIdentityKnown(false)) {
                                    postLogin(commHandler, false);
                                } else if(!commHandler.isIdentityKnown(false)) {
                                    postCreateAccount(commHandler, false);
                                } else {
                                    handler.post(() -> txtLoginPassword.setText(""));
                                    toastErrorMessage(true);
                                    storage.clear();
                                }
                            } catch (Exception e) {
                                Log.e(TAG, e.getMessage(), e);
                                handler.post(() -> Snackbar.make(rootView, e.getMessage(), Snackbar.LENGTH_LONG).show());
                                storage.clear();
                                storage.clearQuickPass(SimplifiedActivity.this);
                            } finally {
                                commHandler.clearLastResponse();
                                storage.clear();
                                handler.post(() -> {
                                    txtLoginPassword.setText("");
                                    progressPopupWindow.dismiss();
                                });
                            }
                        });
                        commHandler.showAskDialog();
                    }).start();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        popupView.findViewById(R.id.btnCloseLogin).setOnClickListener(v -> loginPopupWindow.dismiss());
        popupView.findViewById(R.id.btnLoginOptions).setOnClickListener(v -> {
            loginPopupWindow.dismiss();
            loginOptionsPopupWindow.showAtLocation(loginOptionsPopupWindow.getContentView(), Gravity.CENTER, 0, 0);
        });

        popupView.findViewById(R.id.btnLogin).setOnClickListener(v -> {
            SharedPreferences sharedPref = this.getApplication().getSharedPreferences(
                    APPS_PREFERENCES,
                    Context.MODE_PRIVATE
            );
            long currentId = sharedPref.getLong(CURRENT_ID, 0);

            if(currentId != 0) {
                loginPopupWindow.dismiss();
                progressPopupWindow.showAtLocation(progressPopupWindow.getContentView(), Gravity.CENTER, 0, 0);
                closeKeyboard();

                new Thread(() -> {
                    boolean decryptionOk = storage.decryptIdentityKey(txtLoginPassword.getText().toString(), entropyHarvester, false);
                    if(!decryptionOk) {
                        Snackbar.make(rootView, getString(R.string.decrypt_identity_fail), Snackbar.LENGTH_LONG).show();
                        handler.post(() -> {
                            txtLoginPassword.setText("");
                            progressPopupWindow.dismiss();
                        });
                        storage.clear();
                        return;
                    }
                    showClearNotification();


                    try {
                        postQuery(commHandler, true, false);
                    } catch (Exception e) {
                        handler.post(() -> {
                            Snackbar.make(rootView, e.getMessage(), Snackbar.LENGTH_LONG).show();
                            progressPopupWindow.dismiss();
                        });
                        Log.e(TAG, e.getMessage(), e);
                        commHandler.clearLastResponse();
                        storage.clear();
                        return;
                    } finally {
                        handler.post(() -> txtLoginPassword.setText(""));
                    }

                    commHandler.setAskAction(() -> {
                        handler.post(() -> {
                            progressPopupWindow.showAtLocation(progressPopupWindow.getContentView(), Gravity.CENTER, 0, 0);
                        });
                        try {
                            if(commHandler.isIdentityKnown(false)) {
                                postLogin(commHandler, false);
                            } else if(!commHandler.isIdentityKnown(false)) {
                                postCreateAccount(commHandler, false);
                            } else {
                                handler.post(() -> txtLoginPassword.setText(""));
                                toastErrorMessage(true);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, e.getMessage(), e);
                            handler.post(() -> Snackbar.make(rootView, e.getMessage(), Snackbar.LENGTH_LONG).show());
                        } finally {
                            commHandler.clearLastResponse();
                            storage.clear();
                            handler.post(() -> {
                                txtLoginPassword.setText("");
                                progressPopupWindow.dismiss();
                            });
                        }
                    });
                    commHandler.showAskDialog();

                }).start();
            }
        });
    }


    @Override
    protected void onResume() {
        super.onResume();

        boolean runningTest = getIntent().getBooleanExtra("RUNNING_TEST", false);
        if(runningTest) return;

        if(!mDbHelper.hasIdentities()) {
            startActivity(new Intent(this, StartActivity.class));
        } else {
            SharedPreferences sharedPref = this.getApplication().getSharedPreferences(
                    APPS_PREFERENCES,
                    Context.MODE_PRIVATE
            );
            long currentId = sharedPref.getLong(CURRENT_ID, 0);
            if(currentId != 0) {
                byte[] identityData = mDbHelper.getIdentityData(currentId);
                SQRLStorage storage = SQRLStorage.getInstance();
                try {
                    storage.read(identityData);
                } catch (Exception e) {
                    handler.post(() -> Snackbar.make(rootView, e.getMessage(), Snackbar.LENGTH_LONG).show());
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if(result != null) {
            if(result.getContents() == null) {
                Log.d("MainActivity", "Cancelled scan");
                Snackbar.make(rootView, R.string.scan_cancel, Snackbar.LENGTH_LONG).show();
                if(!mDbHelper.hasIdentities()) {
                    startActivity(new Intent(this, StartActivity.class));
                }
            } else {
                serverData = Utils.readSQRLQRCodeAsString(result.getRawBytes());
                commHandler.setUseSSL(serverData.startsWith("sqrl://"));

                int indexOfQuery = serverData.indexOf("/", serverData.indexOf("://") + 3);
                if(indexOfQuery == -1) {
                    handler.post(() -> Snackbar.make(rootView, R.string.scan_incorrect, Snackbar.LENGTH_LONG).show());
                    return;
                }

                queryLink = serverData.substring(indexOfQuery);
                final String domain = serverData.split("/")[2];
                try {
                    commHandler.setDomain(domain);
                } catch (Exception e) {
                    handler.post(() -> Snackbar.make(rootView, e.getMessage(), Snackbar.LENGTH_LONG).show());
                    Log.e(TAG, e.getMessage(), e);
                    return;
                }

                handler.postDelayed(() -> {
                    final TextView txtSite = loginPopupWindow.getContentView().findViewById(R.id.txtSite);
                    txtSite.setText(domain);
                    loginPopupWindow.showAtLocation(loginPopupWindow.getContentView(), Gravity.CENTER, 0, 0);
                }, 100);
            }
        }
    }

}
