import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.event.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.util.*;
import java.io.*;


/*Classe UserPanel pour modéliser
* le panel de chaque utilisateur
* */

class UserPanel extends JPanel{

	private static final long serialVersionUID = 1L;
	private JTextPane editor;
	private JScrollPane editorScrollPane;
	private JLabel label;
	
	public UserPanel(String username){
		super();
		editor = new JTextPane();
        editor.setPreferredSize(new Dimension(400,50));
        editorScrollPane = new JScrollPane(editor);       
        editor.setDocument(new DefaultStyledDocument());
        editor.setEditable(false);
        label =new JLabel(username);
        this.add(label);
        this.add(editorScrollPane);
	}
	
	public JTextPane getEditor() {
		return editor;
	}
	public JScrollPane getEditorSP() {
		return editorScrollPane;
	}
}

/*Classe Info pour encapsuler quelques variables 
* que j'ai besoin dans les fonctions lambda
* comme deliverCallback et les Listeners
* */

class Info {
	private int nbuser;
	private boolean registered = false;
	private String entry;
	

	public String getEntry() {
		return entry;
	}
	public void setEntry(String ent) {
		entry=ent;
	}
	public boolean getReg() {
		return registered;
	}
	public void setReg(boolean reg) {
		registered=reg;
	}
	public int getNb() {
		return nbuser;
	}
	public void setNb(int n) {
		nbuser=n;
	}
}

/*Classe Editeur qui est l'interface utilisateur
* et tous ses fonctions
* */

public class Editor {
	private final static String QUEUE_NAME_SERV = "SERV";
	
	/*La première fonction main pour prendre
	 * le nom d'utilisateur avec une frame
	 */
	public static void main(String[] args)  {
		Info info = new Info();
		
		JFrame framename =new JFrame("Donner votre nom d'utilisateur:");
		framename.setLayout(new GridLayout(2,1));		
		JTextArea ta = new JTextArea();
		ta.getDocument().putProperty("filterNewlines",Boolean.TRUE);
		ta.setSize(50,1);
		JButton ok = new JButton("Ok");
		ok.setPreferredSize(new Dimension(100,50));
		framename.setSize(600,150);
		framename.add(ta);
		framename.add(ok);
		framename.setVisible(true);
				
		ok.addActionListener(new ActionListener() {
		    @Override
		    public void actionPerformed(ActionEvent e) {
		    	if(!ta.getText().equals("")) {
		    		info.setEntry(ta.getText());			//Listener pour le bouton ok
		    		framename.dispose();					//Qui prend le texte et lance la deuxième partie
		    		try {
						main2(info);
					} catch (Exception e1) {}		    		
		    	}	    		
		    }
		});
	}
	
	/*La deuxième fonction main est le reste du programme
	 * qui gère l'edition de texte avec les autres utilisateurs
	 */
	public static void main2 (Info info) throws Exception{
		ArrayList<String> users = new ArrayList<String>();								//Liste des usernames
		HashMap<String, UserPanel> userpanels = new HashMap<String, UserPanel>();		//Map des panels des utilisateurs	
	    String username=info.getEntry().replace(' ','_');								
	    
		JFrame frame = new JFrame("Editeur ("+username+")");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setLayout(new BorderLayout(10,20));
        JLabel titre =new JLabel("Serveur Déconnecté",SwingConstants.CENTER);		// Création du Frame
        frame.add(titre,BorderLayout.PAGE_START);
        frame.setSize(600,150);
        frame.revalidate();
        frame.setVisible(true);
        
        JPanel mainpanel = new JPanel();
        GridLayout gl = new GridLayout(info.getNb(),1);								//Paneau Principal
        mainpanel.setLayout(gl);
        frame.add(mainpanel,BorderLayout.CENTER);
		
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost("localhost");
		Connection connection = factory.newConnection();						
		Channel channelsen = connection.createChannel();							//Channel for send to server
		channelsen.queueDeclare(QUEUE_NAME_SERV, false,false,false,null);
		
		String message = "1"+username;
		channelsen.basicPublish("", QUEUE_NAME_SERV, null, message.getBytes());		//envoi du nouveau username
		System.out.println(" [x] Sent to server'"+ message +"'");
			
		Channel channelrec = connection.createChannel();							//Channel for send to users  and recieve from server and users
		channelrec.queueDeclare("MQ"+username, false,false,false,null);
		
		/*Ce DeliverCallback va gérer les changements entre les utilisateurs
		 * suivant l'opreration du message reçu
		 */
		
		DeliverCallback deliverCallback = (consumerTag, delivery) -> {
			String nomuser;
			String texte;
			int offset;
			int length;
			
			String messages = new String(delivery.getBody(), "UTF-8");
			System.out.println(" [x] Received '"+ messages +"'");
			int op = Character.getNumericValue(messages.charAt(0));					//Le 1er caractere est le type d'operation
			String content = messages.substring(1);									//Le reste est le contenu qui diffère
			
			switch(op) {
			
			/*Opération 1: se fait une fois au lancement
			 * et charge les données des utilisateurs existants
			 */
			case 1:
			if (!info.getReg()) {	
				
					info.setNb(Integer.parseInt(content.substring(0,content.indexOf(' '))));				//extraction du nombre d'utilisateur	
					content=content.substring(content.indexOf(' ')+1);
					frame.setSize(600, info.getNb()*50+150);
					
					for (int i=0;i<info.getNb();i++) {
						users.add(content.substring(0,content.indexOf(' ')));		//extraction des nom d'utilisateurs
						content=content.substring(content.indexOf(' ')+1);				      
				    }
					
					for (int i=0;i<info.getNb();i++) {
						UserPanel temp = new UserPanel(users.get(i));
						userpanels.put(users.get(i), temp);							//Remplissage de la map avec les Userpanels
						mainpanel.add(temp);
					}
					
					userpanels.get(username).getEditorSP().setBorder(new LineBorder(Color.BLUE, 2, true));
					userpanels.get(username).getEditor().setEditable(true);										//Distinction du panel de cet utilisateur
		        
					/*On ajoute un listener pour le document de cet utilisateur
					 * qui va envoyer les changements du texte au autres utilisateurs
					 */
					userpanels.get(username).getEditor().getDocument().addDocumentListener(new DocumentListener() {
						public void changedUpdate(DocumentEvent e) {
							// rien	        		
						}

						public void insertUpdate(DocumentEvent e) {
							Document doc = (Document)e.getDocument();
							String text="";
							try {
								text = doc.getText(e.getOffset(), e.getLength());					// A l'insertion, le message se comporte de l'utilisateur
							} catch (BadLocationException e1) {										// l'offset et le nouveau texte inséré
								e1.printStackTrace();
							}				        
							String message ="4"+username+" "+e.getOffset()+" "+text; 			
							send(message);								
						}

						public void removeUpdate(DocumentEvent e) {
							String message ="5"+username+" "+e.getOffset()+" "+e.getLength();		// A la suppression, le message se comporte de l'utilisateur
							send(message);															// l'offset et la longueur du texte supprimé
						}
					
						public void send(String message) {
								
									for (int i=0;i<info.getNb();i++) {
										if (!username.equals(users.get(i))) {
											try {
												channelrec.queueDeclare("MQ"+users.get(i), false,false,false,null);			//On envoie le changement à tous les utilisateurs
												channelrec.basicPublish("", "MQ"+users.get(i), null, message.getBytes());
											} catch (IOException e) {
												e.printStackTrace();
											}								    
											//System.out.println(" [x] Sent to "+users.get(i)+" '"+ message +"'");
										}
									}
						}
					});		       		     		        
					info.setReg(true);
		    }
			break;
			
			/*Opération 2: se fait pour ajouter un nouvel utilisateur		
			 */
			case 2:
					info.setNb(info.getNb()+1);
					frame.setSize(600, info.getNb()*50+150);
					users.add(content);
					UserPanel temp = new UserPanel(content);			//L'ajout au frame et a la map
					userpanels.put(content, temp);
					mainpanel.add(temp);
					
					String message2 = "3"+username+" "+userpanels.get(username).getEditor().getText();
					try {
						channelrec.queueDeclare("MQ"+content, false,false,false,null);				//Envoyer le texte de cet utilisateur au nouvel utilisateur
						channelrec.basicPublish("", "MQ"+content, null, message2.getBytes());	
					} catch (IOException e) {
						e.printStackTrace();
					}	        	
			break;
			
			/*Opération 0: se fait pour supprimer un utilisateur sorti
			 */
			case 0:
				info.setNb(info.getNb()-1);
				frame.setSize(600, info.getNb()*50+150);
				users.remove(content);
				mainpanel.remove(userpanels.get(content));
				userpanels.remove(content);
			break;
			
			/*Opération 3: se fait pour recevoir tous les textes des autres utilisateurs lors du lancement
			 */
			case 3:
				 nomuser=content.substring(0,content.indexOf(' '));
				content=content.substring(content.indexOf(' ')+1);
				userpanels.get(nomuser).getEditor().setText(content);
			break;
			
			/*Opération 4: se fait pour effectuer le changement d'insertion
			 */
			case 4:
				 nomuser=content.substring(0,content.indexOf(' '));
				 content=content.substring(content.indexOf(' ')+1);
				 offset=Integer.parseInt(content.substring(0,content.indexOf(' ')));
				 texte=content.substring(content.indexOf(' ')+1);				 
				 userpanels.get(nomuser).getEditor().setText(userpanels.get(nomuser).getEditor().getText().substring(0,offset)+texte+userpanels.get(nomuser).getEditor().getText().substring(offset));
			break;
			
			/*Opération 4: se fait pour effectuer le changement de suppression
			 */
			case 5:
				nomuser=content.substring(0,content.indexOf(' '));
				 content=content.substring(content.indexOf(' ')+1);
				 offset=Integer.parseInt(content.substring(0,content.indexOf(' ')));
				 length=Integer.parseInt(content.substring(content.indexOf(' ')+1));
				 userpanels.get(nomuser).getEditor().setText(userpanels.get(nomuser).getEditor().getText().substring(0,offset)+userpanels.get(nomuser).getEditor().getText().substring(offset+length));
			break;
			}
			
			titre.setText("Editeur de texte repartis");
        	frame.revalidate();								
		};	
		channelrec.basicConsume("MQ"+username, true, deliverCallback,consumerTag -> {});

		
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
            	String message = "0"+username;
        		try {																				//Listener pour enovoyer au serveur
					channelsen.basicPublish("", QUEUE_NAME_SERV, null, message.getBytes());			//que l'utilisateur a quitté
				} catch (IOException e) {
					e.printStackTrace();
				}
        			System.out.println(" [x] Sent to server'"+ message +"'");
            	frame.dispose();
                System.exit(0);
            }
        });

	}

}
