package com.simplestockapp;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class MainActivity extends Activity {

    int itemSelected = 0;
    ActionMode mMode;
    ListView stockList;
    SimpleAdapter adapter;
    HashMap<String, HashMap<String, String>> cacheRow = null;

    private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
            MenuInflater inflater = actionMode.getMenuInflater();
            inflater.inflate(R.menu.stock_item_menu, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.delete) {
                final HashMap<String, String> map = (HashMap<String, String>) stockList.getItemAtPosition(itemSelected);
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        HttpClient httpclient = new DefaultHttpClient();
                        HttpGet httpget = new HttpGet("http://192.168.1.43:3000/delete/" + map.get("symbol").toString());
                        try {
                            httpclient.execute(httpget);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
                t.start();
                actionMode.finish();
            }
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode actionMode) {
            mMode = null;
        }
    };

    void setupSocketIO(final List<HashMap<String,String>> data) {
        try {
            final Socket socket = IO.socket("http://192.168.1.43:3000");
            socket.on("message", new Emitter.Listener() {

                @Override
                public void call(Object... args) {
                    JSONObject j = (JSONObject)args[0];
                    try {
                        String symbol = j.get("symbol").toString();
                        String price = j.get("price").toString();
                        String history = j.getString("history");

                        HashMap<String, String> row = cacheRow.get(symbol);
                        if(row == null) {
                            row = new HashMap<String, String>();
                            row.put("symbol", symbol);
                            row.put("price", price);
                            row.put("history", history);
                            data.add(row);
                            cacheRow.put(symbol, row);
                        } else {
                            row.put("symbol", symbol);
                            row.put("price", price);
                            row.put("history", history);
                        }

                        ((Activity)stockList.getContext()).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                adapter.notifyDataSetChanged();
                            }
                        });

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }).on("delete", new Emitter.Listener(){

                @Override
                public void call(Object... args) {
                    JSONObject j = (JSONObject)args[0];
                    String symbol = null;
                    try {
                        symbol = j.get("symbol").toString();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    HashMap<String, String> row = cacheRow.get(symbol);
                    data.remove(row);

                    ((Activity)stockList.getContext()).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            adapter.notifyDataSetChanged();
                        }
                    });
                }
            });
            socket.connect();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    void setupAddButton() {
        final Button button = (Button) findViewById(R.id.Add);
        button.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View view) {
                EditText stockText = ((EditText) findViewById(R.id.stockText));
                final String stockToAdd = stockText.getText().toString();
                stockText.setText("");
                InputMethodManager imm = (InputMethodManager)getSystemService(
                        Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(stockText.getWindowToken(), 0);

                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        HttpClient httpclient = new DefaultHttpClient();
                        HttpGet httpget = new HttpGet("http://192.168.1.43:3000/add/" + stockToAdd);
                        try {
                            httpclient.execute(httpget);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
                t.start();
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cacheRow = new HashMap<String, HashMap<String, String>>();
        stockList = (ListView)findViewById(R.id.stockList);
        final List<HashMap<String,String>> data = new ArrayList<HashMap<String,String>>();

        adapter = new SimpleAdapter(this,
                data,
                R.layout.stock_layout,
                new String[] { "symbol", "price", "history" },
                new int[] { R.id.symbol, R.id.price, R.id.history });
        stockList.setAdapter(adapter);

        stockList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener(){

            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                itemSelected = i;

                if(mMode != null)
                    return false;

                mMode = ((Activity)view.getContext()).startActionMode(mActionModeCallback);
                return true;
            }
        });

        setupAddButton();
        setupSocketIO(data);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
