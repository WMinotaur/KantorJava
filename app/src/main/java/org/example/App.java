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
         try {
        String log4jConfigPath = "C:\\github\\KantorJava\\app\\src\\main\\resources\\log4j.xml";
        PropertyConfigurator.configure(log4jConfigPath);
        logger.info("Log4j initialized successfully.");
    } catch (Exception ex) {
        logger.fatal("FATAL: Log4j configuration failed. Application will exit.", ex);
        System.exit(1);
    }
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
            JLabel label = new JLabel("Wybierz źródło danych:");
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

            JButton drawDiagramButton = new JButton("Narysuj wykresy kursów");
            topPanel.add(drawDiagramButton);

            JPanel dynamicPanel = new JPanel(new BorderLayout());
            JScrollPane scrollPane = null; // niepotrzebny, tylko placeholder

            JPanel bottomPanel = new JPanel();
            JLabel StatusLabel = new JLabel("Ready...");
            bottomPanel.add(StatusLabel);

            JPanel chartPanelContainer = new JPanel();
            chartPanelContainer.setLayout(new GridLayout(1, 2));
            chartPanelContainer.setVisible(true);

            // Panel do przeliczania walut - nowa linia
            JPanel converterPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            converterPanel.setVisible(false);
            JTextField amountField1 = new JTextField(10);
            JTextField amountField2 = new JTextField(10);
            JButton convertButton = new JButton("Przelicz");
            converterPanel.add(new JLabel("Kwota:"));
            converterPanel.add(amountField1);
            converterPanel.add(new JLabel(" = "));
            converterPanel.add(amountField2);
            converterPanel.add(convertButton);

            JPanel middlePanel = new JPanel();
            middlePanel.setLayout(new BorderLayout());

            middlePanel.add(dynamicPanel, BorderLayout.CENTER);
            middlePanel.add(converterPanel, BorderLayout.SOUTH);
            middlePanel.add(chartPanelContainer, BorderLayout.NORTH);
            middlePanel.setVisible(true);

            panel.add(topPanel, BorderLayout.NORTH);
            panel.add(middlePanel, BorderLayout.CENTER);
            panel.add(bottomPanel, BorderLayout.SOUTH);

            frame.add(panel);

            // logika widoczności dla NBP
            dataSourceComboBox.addActionListener(e -> {
                String selected = (String) dataSourceComboBox.getSelectedItem();
                boolean isNBP = "NBP".equals(selected);
                nbpCurrencyComboBox1.setVisible(isNBP);
                nbpCurrencyComboBox2.setVisible(isNBP);
                drawDiagramButton.setVisible(isNBP);
                converterPanel.setVisible(isNBP);
                if (isNBP) {
                    if (nbpCurrencyComboBox1.getItemCount() == 0) {
                        fetchNBPCurrencies(nbpCurrencyComboBox1, StatusLabel, nbpCurrencyComboBox2);
                    }
                    dynamicPanel.setVisible(false);
                    chartPanelContainer.setVisible(true);
                } else {
                    dynamicPanel.setVisible(true);
                    chartPanelContainer.setVisible(false);
                    converterPanel.setVisible(false);
                    fetchExchangeRates(selected, dynamicPanel, StatusLabel); 
                }
                chartPanelContainer.removeAll();
                chartPanelContainer.revalidate();
                chartPanelContainer.repaint();
            });

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

            drawDiagramButton.addActionListener(e -> {
                String dataSource = (String) dataSourceComboBox.getSelectedItem();
                if ("NBP".equals(dataSource)
                        && nbpCurrencyComboBox1.isVisible()
                        && nbpCurrencyComboBox1.getSelectedItem() != null) {
                    String currencyCode1 = nbpRates.get(nbpCurrencyComboBox1.getSelectedItem().toString());
                    String currencyCode2 = nbpRates.get(nbpCurrencyComboBox2.getSelectedItem().toString());
                    if (currencyCode1 == null) {
                        StatusLabel.setText("Nie wybrano waluty.");
                        logger.warn("Użytkownik nie wybrał pierwszej waluty do wykresu.");

                        return;
                    }
                    if (currencyCode2 == null) {
                        StatusLabel.setText("Nie wybrano drugiej waluty.");
                        logger.warn("Użytkownik nie wybrał drugiej waluty do wykresu.");
                        return;
                    }

                    fetchAndShowNBPCharts(currencyCode1, currencyCode2, chartPanelContainer, StatusLabel);
                } else {
                    fetchExchangeRates(dataSource, dynamicPanel, StatusLabel); 
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
                boolean calc1 = !text1.isEmpty();
                try {
                    double fromRate = fetchNBPLatestRate(from);
                    double toRate = fetchNBPLatestRate(to);
                    if (calc1) {
                        double val = Double.parseDouble(text1);
                        double result = val * fromRate / toRate;
                        amountField2.setText(String.format("%.4f", result));
                    } else {
                        StatusLabel.setText("Wpisz kwotę w pierwsze pole.");
                    }
                } catch (Exception ex) {
                    StatusLabel.setText("Błąd przeliczenia: " + ex.getMessage());
                }
            });

            initiateWindow(frame, topPanel, dataSourceComboBox, nbpCurrencyComboBox1, nbpCurrencyComboBox2, chartPanelContainer, StatusLabel);

            frame.setVisible(true);
        });
    }

    private static void initiateWindow(JFrame frame, JPanel topPanel,JComboBox<String> dataSourceComboBox, JComboBox<String> nbpCurrencyComboBox1,
                                        JComboBox<String> nbpCurrencyComboBox2, JPanel chartPanelContainer, JLabel StatusLabel) {
        frame.setTitle("Kantor - aplikacja do przeliczania walut");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1400, 900);
        frame.setLocationRelativeTo(null);
         for (ActionListener al: dataSourceComboBox.getActionListeners()) {
            al.actionPerformed(new ActionEvent(dataSourceComboBox, ActionEvent.ACTION_PERFORMED, null));
        }
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
                    nbpCurrencyComboBox2.removeAllItems();
                    String chosen = (String)nbpCurrencyComboBox1.getSelectedItem();
                    for (int i = 0; i < nbpCurrencyComboBox1.getItemCount(); i++) {
                        String val = nbpCurrencyComboBox1.getItemAt(i);
                        if (!val.equals(chosen)) {
                            nbpCurrencyComboBox2.addItem(val);
                        }
                    }
                    StatusLabel.setText("Waluty NBP załadowane.");
                    logger.info("Waluty NBP załadowane.");
                    if (nbpRates.isEmpty()) {
                        logger.fatal("Nie udało się załadować żadnej waluty z NBP. Krytyczny błąd.");
                    }

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

    private static void fetchAndShowNBPCharts(String currencyCode1, String currencyCode2, JPanel chartPanelContainer, JLabel statusLabel) {
        SwingUtilities.invokeLater(() -> {
            chartPanelContainer.removeAll();
            chartPanelContainer.setVisible(true);
            try {
                DefaultCategoryDataset dataset30_1 = fetchNBPRatesDataset(currencyCode1, 30);
                JFreeChart chart30_1 = ChartFactory.createLineChart(
                        "Kurs " + currencyCode1 + " (ostatnie 30 dni)", "Data", "Kurs", dataset30_1);

                scaleYAxisToData(chart30_1, dataset30_1);
                setXAxisDateFormat(chart30_1);

                ChartPanel chartPanel30_1 = new ChartPanel(chart30_1);

                DefaultCategoryDataset dataset30_2 = fetchNBPRatesDataset(currencyCode2, 30);
                
                JFreeChart chart30_2 = ChartFactory.createLineChart(
                        "Kurs " + currencyCode2 + " (ostatnie 30 dni)", "Data", "Kurs", dataset30_2);

                scaleYAxisToData(chart30_2, dataset30_2);
                setXAxisDateFormat(chart30_2);

                ChartPanel chartPanel30_2 = new ChartPanel(chart30_2);

                chartPanelContainer.setLayout(new GridLayout(1, 2));
                chartPanelContainer.add(chartPanel30_1);
                chartPanelContainer.add(chartPanel30_2);
                chartPanelContainer.revalidate();
                chartPanelContainer.repaint();
                statusLabel.setText("Kursy pobrane.");
            } catch (Exception ex) {
                statusLabel.setText("Błąd pobierania wykresów: " + ex.getMessage());
            }
        });
    }
    private static void setXAxisDateFormat(JFreeChart chart) {
        CategoryPlot plot = chart.getCategoryPlot();        
        plot.getDomainAxis().setCategoryLabelPositions(
                org.jfree.chart.axis.CategoryLabelPositions.createUpRotationLabelPositions(Math.PI / 6.0));
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

    private static void fetchExchangeRates(String dataSource, JPanel dynamicPanel, JLabel statusLabel) {
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
                    showTextInPanel("Unknown data source selected.", dynamicPanel);
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

                switch (dataSource) {
                    case "NBP":
                        showTextInPanel(parseNBPResponse(content.toString()), dynamicPanel);
                        break;
                    case "ExchangeRate API":
                        showRatesTable(parseExchangeRateAPIObject(content.toString()), dynamicPanel, "ExchangeRate API Exchange Rates:");
                        break;
                    case "Open Exchange Rates":
                        showRatesTable(parseOpenExchangeRatesObject(content.toString()), dynamicPanel, "Open Exchange Rates:");
                        break;
                    default:
                        showTextInPanel("Unknown data source.", dynamicPanel);
                }
                statusLabel.setText("Kursy pobrane z " + dataSource + ".");
                logger.info("Kursy pobrane z " + dataSource);
            } else {
                String errorMessage = "Błąd: " + con.getResponseCode() + " " + con.getResponseMessage();
                showTextInPanel(errorMessage, dynamicPanel);
                logger.error(errorMessage);
                statusLabel.setText("Błąd pobierania kursów: " + con.getResponseCode() + " " + con.getResponseMessage());
            }
        } catch (Exception e) {
            String errorMessage = "Błąd podczas pobierania kursów : " + e.getMessage();
            showTextInPanel(errorMessage, dynamicPanel);
            logger.error(errorMessage, e);
        }
    }

    private static void showTextInPanel(String text, JPanel panel) {
        panel.removeAll();
        JTextArea textArea = new JTextArea(text);
        textArea.setEditable(false);
        panel.add(new JScrollPane(textArea), BorderLayout.CENTER);
        panel.revalidate();
        panel.repaint();
    }

    private static void showRatesTable(JSONObject rates, JPanel panel, String title) {
        java.util.List<String> keys = new ArrayList<>();
        for (String key : rates.keySet()) {
            keys.add(key);
        }
        Collections.sort(keys);

        String[] columnNames = { "Waluta", "Kurs" };
        String[][] data = new String[keys.size()][2];
        for (int i = 0; i < keys.size(); i++) {
            data[i][0] = keys.get(i);
            data[i][1] = rates.get(keys.get(i)).toString();
        }
        JTable table = new JTable(data, columnNames);
        table.setAutoCreateRowSorter(true);
        JScrollPane scrollPane = new JScrollPane(table);

        panel.removeAll();
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        titlePanel.add(new JLabel(title));
        panel.add(titlePanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.revalidate();
        panel.repaint();
    }

    private static String parseNBPResponse(String jsonResponse) {
        StringBuilder result = new StringBuilder("NBP Exchange Rates:\n");
        return result.toString();
    }

    private static JSONObject parseExchangeRateAPIObject(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            return jsonObject.getJSONObject("conversion_rates");
        } catch (Exception e) {
            logger.error("Error parsing ExchangeRate API response", e);
            return new JSONObject();
        }
    }

    private static JSONObject parseOpenExchangeRatesObject(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            return jsonObject.getJSONObject("rates");
        } catch (Exception e) {
            logger.error("Error parsing Open Exchange Rates response", e);
            return new JSONObject();
        }
    }
}