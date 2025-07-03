package org.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.axis.NumberAxis;

public class App {

    private static final Logger logger = Logger.getLogger(App.class);

    static {
        String log4jConfigPath = "C:\\github\\KantorJava\\app\\src\\main\\resources\\log4j2.xml";
        PropertyConfigurator.configure(log4jConfigPath);
    }

    private static Map<String, String> nbpRates = new LinkedHashMap<>();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Kantor");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1400, 900);

            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());

            JPanel topPanel = new JPanel();
            JLabel label = new JLabel("Select Data Source:");
            String[] dataSources = {"NBP", "ExchangeRate API", "Open Exchange Rates"};
            JComboBox<String> dataSourceComboBox = new JComboBox<>(dataSources);
            topPanel.add(label);
            topPanel.add(dataSourceComboBox);

            JComboBox<String> nbpCurrencyComboBox1 = new JComboBox<>();
            nbpCurrencyComboBox1.setVisible(false);
            topPanel.add(nbpCurrencyComboBox1);

            JComboBox<String> nbpCurrencyComboBox2 = new JComboBox<>();
            nbpCurrencyComboBox2.setVisible(false);
            topPanel.add(nbpCurrencyComboBox2);

            JButton fetchButton = new JButton("Fetch Exchange Rates");
            topPanel.add(fetchButton);

            JTextArea textArea = new JTextArea();
            textArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(textArea);

            JPanel bottomPanel = new JPanel();
            JLabel StatusLabel = new JLabel("Ready...");
            bottomPanel.add(StatusLabel);

            JPanel chartPanelContainer = new JPanel();
            chartPanelContainer.setLayout(new GridLayout(1, 2));

            // Panel do przeliczania walut - nowa linia
            JPanel converterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            converterPanel.setVisible(false);
            JTextField amountField1 = new JTextField(10);
            JTextField amountField2 = new JTextField(10);
            JButton convertButton = new JButton("Przelicz");
            converterPanel.add(new JLabel("Kwota:"));
            converterPanel.add(amountField1);
            converterPanel.add(new JLabel(" = "));
            converterPanel.add(amountField2);
            converterPanel.add(convertButton);

            panel.add(topPanel, BorderLayout.NORTH);
            panel.add(converterPanel, BorderLayout.CENTER); // teraz tu panel z boxami
            panel.add(scrollPane, BorderLayout.WEST);
            panel.add(bottomPanel, BorderLayout.SOUTH);
            panel.add(chartPanelContainer, BorderLayout.SOUTH);

            frame.add(panel);

            // logika widoczności dla NBP
            dataSourceComboBox.addActionListener(e -> {
                String selected = (String) dataSourceComboBox.getSelectedItem();
                boolean isNBP = "NBP".equals(selected);
                nbpCurrencyComboBox1.setVisible(isNBP);
                nbpCurrencyComboBox2.setVisible(isNBP);
                converterPanel.setVisible(isNBP);
                if (isNBP) {
                    if (nbpCurrencyComboBox1.getItemCount() == 0) {
                        fetchNBPCurrencies(nbpCurrencyComboBox1, StatusLabel, nbpCurrencyComboBox2);
                    }
                    scrollPane.setVisible(false);
                    chartPanelContainer.setVisible(true);
                } else {
                    scrollPane.setVisible(true);
                    chartPanelContainer.setVisible(false);
                    converterPanel.setVisible(false);
                }
                chartPanelContainer.removeAll();
                chartPanelContainer.revalidate();
                chartPanelContainer.repaint();
            });

            // przeładowanie drugiego combo po zmianie pierwszego
            nbpCurrencyComboBox1.addActionListener(e -> {
                if (nbpCurrencyComboBox2.isVisible() && nbpCurrencyComboBox1.getItemCount() > 0) {
                    String chosen = (String)nbpCurrencyComboBox1.getSelectedItem();
                    nbpCurrencyComboBox2.removeAllItems();
                    for (int i = 0; i < nbpCurrencyComboBox1.getItemCount(); i++) {
                        String val = nbpCurrencyComboBox1.getItemAt(i);
                        if (!val.equals(chosen)) {
                            nbpCurrencyComboBox2.addItem(val);
                        }
                    }
                }
            });

            fetchButton.addActionListener(e -> {
                String dataSource = (String) dataSourceComboBox.getSelectedItem();
                if ("NBP".equals(dataSource)
                        && nbpCurrencyComboBox1.isVisible()
                        && nbpCurrencyComboBox1.getSelectedItem() != null) {
                    String currencyCode = nbpRates.get(nbpCurrencyComboBox1.getSelectedItem().toString());
                    if (currencyCode == null) {
                        StatusLabel.setText("Nie wybrano waluty.");
                        return;
                    }
                    fetchAndShowNBPCharts(currencyCode, chartPanelContainer, StatusLabel);
                } else {
                    fetchExchangeRates(dataSource, textArea);
                    chartPanelContainer.removeAll();
                    chartPanelContainer.revalidate();
                    chartPanelContainer.repaint();
                }
            });

            // obsługa przeliczania
            convertButton.addActionListener(e -> {
                if (nbpCurrencyComboBox1.getSelectedItem() == null || nbpCurrencyComboBox2.getSelectedItem() == null) {
                    StatusLabel.setText("Wybierz obie waluty.");
                    return;
                }
                String from = nbpRates.get(nbpCurrencyComboBox1.getSelectedItem().toString());
                String to = nbpRates.get(nbpCurrencyComboBox2.getSelectedItem().toString());
                String text1 = amountField1.getText().replace(',', '.');
                String text2 = amountField2.getText().replace(',', '.');
                boolean calc1 = !text1.isEmpty() && (text2.isEmpty() || amountField1.hasFocus());
                boolean calc2 = !text2.isEmpty() && (text1.isEmpty() || amountField2.hasFocus());
                try {
                    double fromRate = fetchNBPLatestRate(from);
                    double toRate = fetchNBPLatestRate(to);
                    if (calc1) {
                        double val = Double.parseDouble(text1);
                        double result = val * fromRate / toRate;
                        amountField2.setText(String.format("%.4f", result));
                    } else if (calc2) {
                        double val = Double.parseDouble(text2);
                        double result = val * toRate / fromRate;
                        amountField1.setText(String.format("%.4f", result));
                    } else {
                        StatusLabel.setText("Wpisz kwotę w jednym z pól.");
                    }
                } catch (Exception ex) {
                    StatusLabel.setText("Błąd przeliczenia: " + ex.getMessage());
                }
            });

            chartPanelContainer.setVisible(false);

            frame.setVisible(true);
        });
    }

    private static void fetchNBPCurrencies(JComboBox<String> nbpCurrencyComboBox1, JLabel StatusLabel, JComboBox<String> nbpCurrencyComboBox2) {
        SwingUtilities.invokeLater(() -> {
            nbpCurrencyComboBox1.removeAllItems();
            nbpRates.clear();
            try {
                URL url = new URL("http://api.nbp.pl/api/exchangerates/tables/A/");
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");
                con.setRequestProperty("User-Agent", "Mozilla/5.0");
                con.setRequestProperty("Accept", "application/json");

                if (con.getResponseCode() == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                    StringBuilder content = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        content.append(line);
                    }
                    in.close();

                    JSONArray array = new JSONArray(content.toString());
                    JSONArray rates = array.getJSONObject(0).getJSONArray("rates");
                    for (int i = 0; i < rates.length(); i++) {
                        JSONObject rate = rates.getJSONObject(i);
                        String currency = rate.getString("currency");
                        String code = rate.getString("code");
                        nbpRates.put(currency, code);
                        nbpCurrencyComboBox1.addItem(currency);
                    }
                    // Ustaw od razu drugi ComboBox
                    nbpCurrencyComboBox2.removeAllItems();
                    String chosen = (String)nbpCurrencyComboBox1.getSelectedItem();
                    for (int i = 0; i < nbpCurrencyComboBox1.getItemCount(); i++) {
                        String val = nbpCurrencyComboBox1.getItemAt(i);
                        if (!val.equals(chosen)) {
                            nbpCurrencyComboBox2.addItem(val);
                        }
                    }
                    StatusLabel.setText("Waluty NBP załadowane.");
                } else {
                    StatusLabel.setText("Błąd pobierania walut NBP.");
                }
            } catch (Exception ex) {
                StatusLabel.setText("Błąd pobierania walut: " + ex.getMessage());
            }
        });
    }

    private static double fetchNBPLatestRate(String code) throws Exception {
        URL url = new URL("https://api.nbp.pl/api/exchangerates/rates/A/" + code + "/last/1/?format=json");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", "Mozilla/5.0");
        con.setRequestProperty("Accept", "application/json");

        if (con.getResponseCode() == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                content.append(line);
            }
            in.close();

            JSONObject jsonObject = new JSONObject(content.toString());
            JSONArray rates = jsonObject.getJSONArray("rates");
            if (rates.length() > 0) {
                return rates.getJSONObject(0).getDouble("mid");
            }
            throw new Exception("Brak kursu dla " + code);
        } else {
            throw new Exception("Błąd pobierania kursu NBP: " + con.getResponseCode());
        }
    }

    private static void fetchAndShowNBPCharts(String currencyCode, JPanel chartPanelContainer, JLabel statusLabel) {
        SwingUtilities.invokeLater(() -> {
            chartPanelContainer.removeAll();
            chartPanelContainer.setVisible(true);
            try {
                DefaultCategoryDataset dataset30 = fetchNBPRatesDataset(currencyCode, 30);
                JFreeChart chart30 = ChartFactory.createLineChart(
                        "Kurs " + currencyCode + " (ostatnie 30 dni)", "Data", "Kurs", dataset30);

                scaleYAxisToData(chart30, dataset30);

                ChartPanel chartPanel30 = new ChartPanel(chart30);

                DefaultCategoryDataset dataset90 = fetchNBPRatesDataset(currencyCode, 90);
                JFreeChart chart90 = ChartFactory.createLineChart(
                        "Kurs " + currencyCode + " (ostatnie 90 dni)", "Data", "Kurs", dataset90);

                scaleYAxisToData(chart90, dataset90);

                ChartPanel chartPanel90 = new ChartPanel(chart90);

                chartPanelContainer.setLayout(new GridLayout(1, 2));
                chartPanelContainer.add(chartPanel30);
                chartPanelContainer.add(chartPanel90);
                chartPanelContainer.revalidate();
                chartPanelContainer.repaint();
                statusLabel.setText("Wykresy pobrane.");
            } catch (Exception ex) {
                statusLabel.setText("Błąd pobierania wykresów: " + ex.getMessage());
            }
        });
    }

    private static void scaleYAxisToData(JFreeChart chart, DefaultCategoryDataset dataset) {
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (int row = 0; row < dataset.getRowCount(); row++) {
            for (int col = 0; col < dataset.getColumnCount(); col++) {
                Number value = dataset.getValue(row, col);
                if (value != null) {
                    double v = value.doubleValue();
                    if (v < min) min = v;
                    if (v > max) max = v;
                }
            }
        }
        double range = max - min;
        double margin = range * 0.02;
        if (margin == 0) margin = max * 0.01;
        if (min == Double.MAX_VALUE || max == Double.MIN_VALUE) return;
        CategoryPlot plot = chart.getCategoryPlot();
        NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
        yAxis.setRange(min - margin, max + margin);
    }

    private static DefaultCategoryDataset fetchNBPRatesDataset(String currencyCode, int days) throws Exception {
        URL url = new URL("https://api.nbp.pl/api/exchangerates/rates/A/" + currencyCode + "/last/" + days + "/?format=json");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", "Mozilla/5.0");
        con.setRequestProperty("Accept", "application/json");

        if (con.getResponseCode() == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                content.append(line);
            }
            in.close();

            JSONObject jsonObject = new JSONObject(content.toString());
            JSONArray rates = jsonObject.getJSONArray("rates");
            DefaultCategoryDataset dataset = new DefaultCategoryDataset();
            for (int i = 0; i < rates.length(); i++) {
                JSONObject rate = rates.getJSONObject(i);
                String date = rate.getString("effectiveDate");
                double value = rate.getDouble("mid");
                dataset.addValue(value, "kurs", date);
            }
            return dataset;
        } else {
            throw new Exception("Błąd pobierania kursów NBP: " + con.getResponseCode());
        }
    }

    private static void fetchExchangeRates(String dataSource, JTextArea textArea) {
        try {
            URL url;
            switch (dataSource) {
                case "NBP":
                    url = new URL("http://api.nbp.pl/api/exchangerates/tables/A/");
                    break;
                case "ExchangeRate API":
                    url = new URL("https://v6.exchangerate-api.com/v6/8e507997dc413b9f1858668b/latest/USD");
                    break;
                case "Open Exchange Rates":
                    url = new URL("https://openexchangerates.org/api/latest.json?app_id=9ce7f33638c547369cbf171d54d9d84e");
                    break;
                default:
                    textArea.setText("Unknown data source selected.");
                    return;
            }

            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", "Mozilla/5.0");
            con.setRequestProperty("Accept", "application/json");

            if (con.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuilder content = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                in.close();

                String result;
                switch (dataSource) {
                    case "NBP":
                        result = parseNBPResponse(content.toString());
                        break;
                    case "ExchangeRate API":
                        result = parseExchangeRateAPIResponse(content.toString());
                        break;
                    case "Open Exchange Rates":
                        result = parseOpenExchangeRatesResponse(content.toString());
                        break;
                    default:
                        result = "Unknown data source.";
                }
                textArea.setText(result);
                logger.info("Exchange rates fetched successfully from " + dataSource);
            } else {
                String errorMessage = "Error: " + con.getResponseCode() + " " + con.getResponseMessage();
                textArea.setText(errorMessage);
                logger.error(errorMessage);
            }
        } catch (Exception e) {
            String errorMessage = "An error occurred: " + e.getMessage();
            textArea.setText(errorMessage);
            logger.error(errorMessage, e);
        }
    }

    private static String parseNBPResponse(String jsonResponse) {
        StringBuilder result = new StringBuilder("NBP Exchange Rates:\n");
        // niepotrzebne w tym widoku, ale zostawione
        return result.toString();
    }

    private static String parseExchangeRateAPIResponse(String jsonResponse) {
        StringBuilder result = new StringBuilder("ExchangeRate API Exchange Rates:\n");
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONObject rates = jsonObject.getJSONObject("conversion_rates");

            for (String key : rates.keySet()) {
                result.append(String.format("%-15s %s\n", key, rates.get(key)));
            }
        } catch (Exception e) {
            result.append("Error parsing ExchangeRate API response: ").append(e.getMessage());
            logger.error("Error parsing ExchangeRate API response", e);
        }

        return result.toString();
    }

    private static String parseOpenExchangeRatesResponse(String jsonResponse) {
        StringBuilder result = new StringBuilder("Open Exchange Rates:\n");

        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONObject rates = jsonObject.getJSONObject("rates");

            for (String key : rates.keySet()) {
                result.append(String.format("%-15s %s\n", key, rates.get(key)));
            }
        } catch (Exception e) {
            result.append("Error parsing Open Exchange Rates response: ").append(e.getMessage());
            logger.error("Error parsing Open Exchange Rates response", e);
        }

        return result.toString();
    }
}