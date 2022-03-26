import java.util.ArrayList;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

//TP1 fait par Melek Elloumi GL3

/*Classe Serveur pour maintenir 
 * le nombre d'utilisateurs connectés
 * */

public class Server {
	private final static String QUEUE_NAME_SERV = "SERV";
	private static int nbuser;
	
	public static void main(String[] args) throws Exception{
		ArrayList<String> users = new ArrayList<String>();        			    // liste de username
		nbuser=0;
		
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost("localhost");
		Connection connection = factory.newConnection();						// Channel for reception
		Channel channelrec = connection.createChannel();
		channelrec.queueDeclare(QUEUE_NAME_SERV, false,false,false,null);
		
		DeliverCallback deliverCallback = (consumerTag, delivery) -> {
			String message = new String(delivery.getBody(), "UTF-8");
			System.out.println(" [x] Received server '"+ message +"'");		
			int op = Character.getNumericValue(message.charAt(0));             //opération à faire
			String content = message.substring(1);
			Channel channelsen = connection.createChannel();
			channelsen.queueDeclare("MQ"+content, false,false,false,null);		// Channel for sending
			switch(op) {
			/*
			 * Ajout d'un nouveau utilisateur connecté
			 */
			case 1:
				users.add(content);
				nbuser++;
				message="1"+nbuser+" ";
				for (String i : users) {									// Mettre les utilisateurs déjà connecté
				      message+=i+" ";
				    }
				
				channelsen.basicPublish("", "MQ"+content, null, message.getBytes());
				System.out.println(" [x] Sent '"+ message +"' to '"+content);
				message="2"+content;
				for (int i=0;i<nbuser;i++) {
					if (!content.equals(users.get(i))) {										//envoyer le nouveau utilisateur aux autres
						channelsen.basicPublish("", "MQ"+users.get(i), null, message.getBytes());
						System.out.println(" [x] Sent '"+ message +"' to '"+users.get(i));
					}
				}
			break;
				
			/*
			 * Suppression d'un utilisateur sorti
			 */
			
			case 0:
				users.remove(content);
				nbuser--;
				message="0"+content;
				for (int i=0;i<nbuser;i++) {
					channelsen.basicPublish("", "MQ"+users.get(i), null, message.getBytes());
					System.out.println(" [x] Sent '"+ message +"' to '"+users.get(i));
				}
			break;
			
			}
								
		};	
		
		channelrec.basicConsume(QUEUE_NAME_SERV, true, deliverCallback,consumerTag -> {});
		System.out.println("waiting");	
	}

}
