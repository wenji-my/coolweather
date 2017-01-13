package com.coolweather.android.fragment;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.coolweather.android.R;
import com.coolweather.android.db.City;
import com.coolweather.android.db.County;
import com.coolweather.android.db.Province;
import com.coolweather.android.util.HttpUtil;
import com.coolweather.android.util.Utility;

import org.litepal.crud.DataSupport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * Created by Administrator on 2017/1/13.
 */

public class ChooseAreaFragment extends Fragment {

	public static final int LEVEL_PROVINCE=0;
	public static final int LEVEL_CITY=1;
	public static final int LEVEL_COUNTY=2;
	private ProgressDialog mProgressDialog;
	private TextView titleText;
	private Button backButton;
	private ListView mListView;
	private ArrayAdapter<String> mAdapter;
	private List<String> dataList=new ArrayList<>();

	private List<Province> mProvinceList;
	private List<City> mCityList;
	private List<County> mCountyList;
	private Province selectedProvince;
	private City selectedCity;
	private int currentLevel;

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.choose_area, container, false);
		titleText= (TextView) view.findViewById(R.id.title_text);
		backButton= (Button) view.findViewById(R.id.back_button);
		mListView= (ListView) view.findViewById(R.id.list_view);
		mAdapter=new ArrayAdapter<String>(getContext(),android.R.layout.simple_list_item_1,dataList);
		mListView.setAdapter(mAdapter);
		return view;
	}

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if (currentLevel==LEVEL_PROVINCE){
					selectedProvince=mProvinceList.get(position);
					queryCities();
				}else if (currentLevel==LEVEL_CITY){
					selectedCity=mCityList.get(position);
					queryCounties();
				}
			}
		});
		backButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (currentLevel==LEVEL_COUNTY){
					queryCities();
				}else if (currentLevel==LEVEL_CITY){
					queryProvinces();
				}
			}
		});
		queryProvinces();
	}

	/**
	 * 查询所有省，优先从数据库查询，没有再去服务器查询
	 */
	private void queryProvinces() {
		titleText.setText("中国");
		backButton.setVisibility(View.GONE);
		mProvinceList= DataSupport.findAll(Province.class);
		if (mProvinceList.size()>0){
			dataList.clear();
			for (Province province:mProvinceList){
				dataList.add(province.getProvinceName());
			}
			mAdapter.notifyDataSetChanged();
			mListView.setSelection(0);
			currentLevel=LEVEL_PROVINCE;
		}else {
			String address="http://guolin.tech/api/china";
			queryFromServer(address,"province");
		}
	}

	/**
	 * 查询省内所有市，优先从数据库查询，没有再去服务器查询
	 */
	private void queryCities() {
		titleText.setText(selectedProvince.getProvinceName());
		backButton.setVisibility(View.VISIBLE);
		mCityList=DataSupport.where("provinceid=?",String.valueOf(selectedProvince.getId())).find(City.class);
		if (mCityList.size()>0){
			dataList.clear();
			for (City city:mCityList){
				dataList.add(city.getCityName());
			}
			mAdapter.notifyDataSetChanged();
			mListView.setSelection(0);
			currentLevel=LEVEL_CITY;
		}else {
			int provinceCode=selectedProvince.getProvinceCode();
			String address="http://guolin.tech/api/china/"+provinceCode;
			queryFromServer(address,"city");
		}
	}

	private void queryCounties(){
		titleText.setText(selectedCity.getCityName());
		backButton.setVisibility(View.VISIBLE);
		mCountyList=DataSupport.where("cityid=?",String.valueOf(selectedCity.getId())).find(County.class);
		if (mCountyList.size()>0){
			dataList.clear();
			for (County county:mCountyList){
				dataList.add(county.getCountyName());
			}
			mAdapter.notifyDataSetChanged();
			mListView.setSelection(0);
			currentLevel=LEVEL_COUNTY;
		}else {
			int provinceCode=selectedProvince.getProvinceCode();
			int cityCode=selectedCity.getCityCode();
			String address="http://guolin.tech/api/china/"+provinceCode+"/"+cityCode;
			queryFromServer(address,"county");
		}
	}

	private void queryFromServer(String address, final String type){
		showProgressDialog();
		HttpUtil.sendOkHttpRequest(address, new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				getActivity().runOnUiThread(new Runnable() {
					@Override
					public void run() {
						closeProgressDialog();
						Toast.makeText(getContext(),"加载失败",Toast.LENGTH_SHORT).show();
					}
				});
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException {
				String responseText = response.body().string();
				boolean result=false;
				if ("province".equals(type)){
					result= Utility.handleProvinceResponse(responseText);
				}else if ("city".equals(type)){
					result=Utility.handleCityResponse(responseText,selectedProvince.getId());
				}else if ("county".equals(type)){
					result=Utility.handleCountyResponse(responseText,selectedCity.getId());
				}
				if (result){
					getActivity().runOnUiThread(new Runnable() {
						@Override
						public void run() {
							closeProgressDialog();
							if ("province".equals(type)){
								queryProvinces();
							}else if ("city".equals(type)){
								queryCities();
							}else if ("county".equals(type)){
								queryCounties();
							}
						}
					});
				}
			}
		});
	}

	private void closeProgressDialog() {
		if (mProgressDialog!=null){
			mProgressDialog.dismiss();
		}
	}

	private void showProgressDialog() {
		if (mProgressDialog==null){
			mProgressDialog=new ProgressDialog(getActivity());
			mProgressDialog.setMessage("正在加载...");
			mProgressDialog.setCanceledOnTouchOutside(false);
		}
		mProgressDialog.show();
	}
}
