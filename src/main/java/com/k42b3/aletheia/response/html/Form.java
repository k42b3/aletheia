/**
 * aletheia
 * A browser like application to send raw http requests. It is designed for 
 * debugging and finding security issues in web applications. For the current 
 * version and more informations visit <http://code.google.com/p/aletheia>
 * 
 * Copyright (c) 2010-2015 Christoph Kappestein <k42b3.x@gmail.com>
 * 
 * This file is part of Aletheia. Aletheia is free software: you can 
 * redistribute it and/or modify it under the terms of the GNU 
 * General Public License as published by the Free Software Foundation, 
 * either version 3 of the License, or at any later version.
 * 
 * Aletheia is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Aletheia. If not, see <http://www.gnu.org/licenses/>.
 */

package com.k42b3.aletheia.response.html;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.k42b3.aletheia.Aletheia;
import com.k42b3.aletheia.processor.ProcessPropertiesAbstract;
import com.k42b3.aletheia.processor.ResponseProcessorInterface;
import com.k42b3.aletheia.protocol.Response;
import com.k42b3.aletheia.protocol.http.Request;
import com.k42b3.aletheia.protocol.http.Util;

/**
 * Form
 *
 * @author     Christoph Kappestein <k42b3.x@gmail.com>
 * @license    http://www.gnu.org/licenses/gpl.html GPLv3
 * @link       http://aletheia.k42b3.com
 */
public class Form extends JFrame implements ResponseProcessorInterface
{
	private ArrayList<FormData> forms = new ArrayList<FormData>();
	private ArrayList<FormTableModel> fields = new ArrayList<FormTableModel>();

	private JTabbedPane tb;
	private String baseUrl;

	public Form()
	{
		super();

		// settings
		this.setTitle("Form");
		this.setLocation(100, 100);
		this.setPreferredSize(new Dimension(360, 400));
		this.setMinimumSize(this.getSize());
		this.setResizable(false);
		this.setLayout(new BorderLayout());

		// tab panel
		tb = new JTabbedPane();

		this.add(tb, BorderLayout.CENTER);
		
		// buttons
		JPanel panelButtons = new JPanel();

		FlowLayout fl = new FlowLayout();
		fl.setAlignment(FlowLayout.LEFT);

		panelButtons.setLayout(fl);

		JButton btnInsert = new JButton("Insert");
		btnInsert.setMnemonic(java.awt.event.KeyEvent.VK_I);
		btnInsert.addActionListener(new InsertHandler());

		panelButtons.add(btnInsert);

		this.add(panelButtons, BorderLayout.SOUTH);


		this.pack();
	}

	public void process(URL url, Response response, Properties properties) throws Exception 
	{
		if(response instanceof com.k42b3.aletheia.protocol.http.Response)
		{
			com.k42b3.aletheia.protocol.http.Response httpResponse = (com.k42b3.aletheia.protocol.http.Response) response;
			
			// reset
			this.reset();

			// set base url
			this.baseUrl = url.toString();

			// parse form
			this.parseForms(httpResponse.getBody());

			// build components
			this.buildElements();
		}
		
		// set visible
		this.pack();
		this.setVisible(true);
	}

	public ProcessPropertiesAbstract getProperties()
	{
		return null;
	}

	private void close()
	{
		this.setVisible(false);
	}

	private void reset()
	{
		this.forms.clear();
		this.fields.clear();
		this.tb.removeAll();
	}

	private void insert()
	{
		if(this.fields.size() > 0)
		{
			StringBuilder response = new StringBuilder();
			FormTableModel model = this.fields.get(this.tb.getSelectedIndex());
			
			for(int i = 0; i < model.getRowCount(); i++)
			{
				boolean active = Boolean.parseBoolean(model.getValueAt(i, 0).toString());
				
				if(active)
				{
					try
					{
						String key = "" + model.getValueAt(i, 1);
						String value = "" + model.getValueAt(i, 2);

						response.append(key + "=" + URLEncoder.encode(value, "UTF-8"));
						response.append("&");
					}
					catch(Exception e)
					{
						Aletheia.handleException(e);
					}
				}
			}

			// append data depending on form method
			String method = forms.get(this.tb.getSelectedIndex()).getMethod();
			String actionUrl = forms.get(this.tb.getSelectedIndex()).getUrl();
			String activeUrl = Aletheia.getInstance().getActiveUrl().getText();

			// build url
			try
			{
				// set url
				String url = Util.resolveHref(activeUrl, actionUrl);

				// get path
				URL currentUrl = new URL(url);
				String path = currentUrl.getPath();

				if(currentUrl.getQuery() != null)
				{
					path = path + "?" + currentUrl.getQuery();
				}

				if(currentUrl.getRef() != null)
				{
					path = path + "#" + currentUrl.getRef();
				}

				// insert query
				Request request = (Request) Aletheia.getInstance().getActiveIn().getRequest();

				if(method.equals("GET"))
				{
					url = Util.appendQuery(url, response.toString());

					Aletheia.getInstance().getActiveUrl().setText(url);

					request.setLine(method, path);
				}
				else if(method.equals("POST"))
				{
					Aletheia.getInstance().getActiveUrl().setText(url);

					request.setLine(method, path);
					request.setHeader("Content-Type", "application/x-www-form-urlencoded");
					request.setBody(response.toString());
				}

				Aletheia.getInstance().getActiveIn().update();
			}
			catch(Exception e)
			{
				Aletheia.handleException(e);
			}
		}

		this.setVisible(false);
	}

	private void buildElements()
	{
		if(forms.size() > 0)
		{
			for(int i = 0; i < forms.size(); i++)
			{
				Set<Entry<String, String>> set = forms.get(i).getValues().entrySet();
				Iterator<Entry<String, String>> iter = set.iterator();

				FormTableModel formModel = new FormTableModel();
				JTable formTable = new JTable(formModel);
				formTable.setRowHeight(24);
				formTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
				formTable.getColumnModel().getColumn(0).setPreferredWidth(40);
				formTable.getColumnModel().getColumn(1).setPreferredWidth(150);
				formTable.getColumnModel().getColumn(2).setPreferredWidth(150);

				while(iter.hasNext())
				{
					Map.Entry<String, String> item = (Map.Entry<String, String>) iter.next();

					Object[] row = {true, item.getKey(), item.getValue()};

					formModel.addRow(row);
				}

				JScrollPane scp = new JScrollPane(formTable);
				scp.setBorder(new EmptyBorder(4, 4, 4, 4));

				tb.addTab("Form #" + i, scp);

				this.fields.add(formModel);
			}
		}
		else
		{
			JPanel containerPanel = new JPanel();
			containerPanel.setLayout(new FlowLayout());
			containerPanel.add(new JLabel("No elements found"));

			tb.addTab("Form #0", containerPanel);
		}
	}

	private void parseForms(String html)
	{
		Document doc = Jsoup.parse(html);
		Elements forms = doc.getElementsByTag("form");
		FormData data = null;

		for(Element form : forms)
		{
			// get method
			String method = form.attr("method");
			if(method.isEmpty())
			{
				method = "GET";
			}

			// get action
			String action = form.attr("action");
			if(action.isEmpty())
			{
				action = baseUrl;
			}

			// create form
			data = new FormData(method, action);

			// input elements
			Elements inputs = form.getElementsByTag("input");
			for(Element input : inputs)
			{
				data.addElement(input.attr("name"), input.attr("value"));
			}

			// textarea elements
			Elements textareas = form.getElementsByTag("textarea");
			for(Element textarea : textareas)
			{
				data.addElement(textarea.attr("name"), textarea.html());
			}

			// select
			Elements selects = form.getElementsByTag("select");
			for(Element select : selects)
			{
				Elements options = select.select("option[selected]");
				String value = "";
				if(options.size() > 0)
				{
					value = options.first().attr("value");
				}

				data.addElement(select.attr("name"), value);
			}

			this.forms.add(data);
		}
	}

	private class FormData
	{
		private String url;
		private String method;
		private HashMap<String, String> values = new HashMap<String, String>();

		public FormData(String method, String url)
		{
			this.setMethod(method);
			this.setUrl(url);
		}

		public FormData(String method)
		{
			this(method, null);
		}

		public String getUrl() 
		{
			return url;
		}

		public void setUrl(String url) 
		{
			this.url = url;
		}

		public String getMethod() 
		{
			return method;
		}

		public void setMethod(String method) 
		{
			if(method == null || method.isEmpty())
			{
				method = "GET";
			}

			method = method.toUpperCase();

			if(method.equals("GET") || method.equals("POST"))
			{
				this.method = method;
			}
			else
			{
				this.method = "GET";
			}
		}

		public void addElement(String name, String value)
		{
			if(name != null && !name.isEmpty())
			{
				values.put(name, value);
			}
		}

		public HashMap<String, String> getValues() 
		{
			return values;
		}
	}

	private class InsertHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent e) 
		{
			insert();
		}
	}
	
	private class FormTableModel extends DefaultTableModel
	{
		private String[] columns = {"", "Key", "Value"};

		public FormTableModel()
		{
		}

		public Class getColumnClass(int columnIndex)
		{
			return columnIndex == 0 ? Boolean.class : String.class;
		}

		public int getColumnCount()
		{
			return columns.length;
		}
		
		public String getColumnName(int column)
		{
			return column >= 0 && column < this.columns.length ? this.columns[column] : null;
		}
		
		public boolean isCellEditable(int row, int column)
		{
			return true;
		}
	}
}
