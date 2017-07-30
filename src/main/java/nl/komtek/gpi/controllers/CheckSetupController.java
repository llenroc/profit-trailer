package nl.komtek.gpi.controllers;

import nl.komtek.gpi.services.GunbotProxyService;
import nl.komtek.gpi.utils.Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Elroy on 10-7-2017.
 */
@Controller

public class CheckSetupController {

	@Autowired
	private GunbotProxyService gunbotProxyService;
	@Autowired
	private Util util;

	@RequestMapping("/checkSetup/**")
	public ModelAndView checkSetup(ModelMap modelMap) {
		modelMap.put("setupData", setupData());
		return new ModelAndView("setupData", modelMap);
	}

	@ResponseBody
	@RequestMapping("/checkSetupLinux/**")
	public String checkSetupLinux() throws IOException {
		StringBuilder stringData = new StringBuilder();
		stringData.append("\n\n");
		Map<String, String> setupData = setupData();
		for (Map.Entry entry : setupData.entrySet()) {
			stringData.append(String.format("%s -- %s \n", entry.getKey(), entry.getValue()));
		}
		return stringData.toString();
	}

	private Map<String, String> setupData() {
		Map<String, String> setupData = new HashMap<>();

		if (!gunbotProxyService.isUsingMultipleMarkets()) {
			checkApiKeysForMarket(setupData, "");
		} else {
			if (gunbotProxyService.isActiveMarket("BTC")) {
				checkApiKeysForMarket(setupData, "BTC_");
			}
			if (gunbotProxyService.isActiveMarket("ETH")) {
				checkApiKeysForMarket(setupData, "ETH_");
			}
			if (gunbotProxyService.isActiveMarket("XMR")) {
				checkApiKeysForMarket(setupData, "XMR_");
			}
			if (gunbotProxyService.isActiveMarket("USDT")) {
				checkApiKeysForMarket(setupData, "USDT_");
			}
		}
		//check if hostfile was setup correctly
		try {
			InetAddress address = InetAddress.getByName("poloniex.com");
			if (address.getHostAddress().equals("127.0.0.1")) {
				setupData.put(String.format("hostfile (%s)", address.getHostAddress()), "Looking good!");
			} else {
				setupData.put(String.format("hostfile (%s)", address.getHostAddress()), "poloniex.com is not pointing to 127.0.0.1");
			}
		} catch (UnknownHostException e) {
			setupData.put("hostfile", e.getMessage());
		}

		try {
			InetAddress address = InetAddress.getByName("www.poloniex.com");
			if (!address.getHostAddress().equals("127.0.0.1")) {
				setupData.put(String.format("www.poloniex.com (%s)", address.getHostAddress()), "Looking good!");
			} else {
				setupData.put(String.format("www.poloniex.com (%s)", address.getHostAddress()), "www.poloniex.com should not point to 127.0.0.1");
			}
		} catch (UnknownHostException e) {
			setupData.put("www.poloniex.com", e.getMessage());
		}

		String gunbotLocation = util.getEnvProperty("gunbot.location");
		if (!StringUtils.isEmpty(gunbotLocation.length())) {
			if (!gunbotLocation.startsWith("file://")) {
				setupData.put("Gunbot location (Optional)", "Your file location should start with 'file://'");
			} else {
				File file = new File(gunbotLocation.replace("file://", ""));
				if (file.exists()) {
					setupData.put("Gunbot location (Optional)", "Looking good!");
				} else {
					setupData.put("Gunbot location (Optional)", "The specified location does not exist");
				}
			}
		} else {
			setupData.put("Gunbot location (Optional)", "You cannot use monitoring without this");
		}
		return setupData;
	}

	private void checkApiKeysForMarket(Map<String, String> setupData, String market) {
		String keyName = String.format("default_%sapiKey", market);
		String secretName = String.format("default_%sapiSecret", market);
		try {
			String apiKey = util.getEnvProperty(keyName);
			String apiSecret = util.getEnvProperty(secretName);
			if (StringUtils.isEmpty(apiKey) || StringUtils.isEmpty(apiSecret)) {
				setupData.put(keyName, String.format("Please setup %s and %s", keyName, secretName));
			} else {
				String theMarket = market.replace("_", "");
				if (StringUtils.isEmpty(theMarket)) {
					theMarket = "default";
				}
				gunbotProxyService.analyzeResult(gunbotProxyService.checkDefaultKey(theMarket));
				setupData.put(keyName, "Looking good!");
			}
		} catch (Exception e) {
			setupData.put(keyName, e.getMessage());
		}

		for (int i = 1; i <= 10; i++) {
			keyName = String.format("%sapiKey%d", market, i);
			secretName = String.format("%sapiSecret%d", market, i);
			String apiKey = util.getEnvProperty(keyName);
			String apiSecret = util.getEnvProperty(secretName);
			if (i == 1 && (StringUtils.isEmpty(apiKey) || StringUtils.isEmpty(apiSecret))) {
				setupData.put(keyName, String.format("Please setup %s and %s", keyName, secretName));
				break;
			}
			if (StringUtils.isEmpty(apiKey) || StringUtils.isEmpty(apiSecret)) {
				break;
			}
			try {
				if (StringUtils.isEmpty(market)) {
					gunbotProxyService.analyzeResult(gunbotProxyService.checkTradingKey(apiKey));
				} else {
					gunbotProxyService.analyzeResult(gunbotProxyService.checkMultiMarketTradingKey(apiKey));
				}
				setupData.put(keyName, "Looking good!");
			} catch (Exception e) {
				setupData.put(keyName, e.getMessage());
			}
		}
	}
}
