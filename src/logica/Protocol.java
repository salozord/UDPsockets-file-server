package logica;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalTime;
import javax.xml.bind.DatatypeConverter;
public class Protocol implements Runnable{

	public static final String PREPARADO = "PREPARADO";
	public static File folder  = new File("./data/");
	public static final String SEPARADOR = "$";
	public static final Integer TAMANIO_SEGMENTO = 32768;
	public static final String ERROR = "ERROR";
	public static final String ARCH = "ARCH";
	public static final String FINARCH = "FINARCH";
	public static final String RECIBIDO = "RECIBIDO";
	public static final String LLEGO = "LLEGO";
	public static final String NEW_LINE = "\n";
	private DatagramPacket dp;
	private boolean aceptaArchs;
	private File archivo;
	private PoolThreads pool;
	private InetAddress direccion;
	private int puerto;
	private FileWriter fw;
	private int id;
	private String estado;

	public Protocol (boolean aceptaArchs, String archivo, PoolThreads poolThreads, InetAddress direccion, int puerto, int id) throws IOException
	{
		this.aceptaArchs = aceptaArchs;
		this.archivo = new File(archivo);
		this.pool = pool;
		this.direccion = direccion;
		this.puerto = puerto;
		this.id = id;
		this.estado = "";
		fw = new FileWriter(new File("./data/logs/Cliente_" + this.id + "_LOG.log" ));
	}

	public void run()
	{
		try 
		{
			int i = 0;
			while(true)
			{
				if(aceptaArchs == true)
				{

					procesar();
					break;
				}
				else
				{
					if(i == 0 ) {
						this.aceptaArchs = pool.seAceptan(true);
						i++;
					}
					else {
						this.aceptaArchs = pool.seAceptan(false);

					}
				}
			}
		} 
		catch (Exception e) 
		{
			e.printStackTrace();
		}
		finally
		{
			if(aceptaArchs == true)
				pool.finSesionArchivos();
			
			pool.finSesion();
			pool.notificar();
		}
	}
	/**
	 * Procesar el servicio
	 * @throws Exception 
	 */
	public void procesar() throws Exception
	{
		try 
		{

			LocalTime ld = LocalTime.now();
			escribirLog("Ingresó nuevo cliente con código " + id);
			File archivoDeseado = archivo;
			if(archivoDeseado != null) 
			{

				//avisamos el nombre del archivo se mandara 
				String header =  "NOMBRE" + SEPARADOR;
				escribirLog("Preparando para enviar nuevo archivo con nombre '" + archivoDeseado.getName() + "' . . .");
				
				header += archivoDeseado.getName();
				byte[] b = header.getBytes();
				dp = new DatagramPacket(b, b.length, direccion, puerto);
				pool.enviar(dp);
				escribirLog("Se envió al cliente el nombre del archivo correctamente");
				
				byte[] mybytearray;
				BufferedInputStream bis;
				MessageDigest hash;

				mybytearray = new byte[TAMANIO_SEGMENTO];
				bis = new BufferedInputStream(new FileInputStream(archivoDeseado));
				
				hash = MessageDigest.getInstance("SHA-256");
				System.out.println("Longi " + archivoDeseado.length());
				escribirLog("Calculando la cantidad de paquetes a enviar al cliente . . .");
				escribirLog("Longitud del archivo a enviar: " + archivoDeseado.length() + " Bytes");
				Long numPaquetes = (long) Math.ceil(((double)archivoDeseado.length())/TAMANIO_SEGMENTO);
				escribirLog("La cantidad de paquetes a enviar, según un buffer de salida de " + TAMANIO_SEGMENTO + " Bytes, es de " + numPaquetes + " paquetes");
				
				// Se envía el número de paquetes a enviar al cliente
				byte[] np =  {numPaquetes.byteValue()};
				dp = new DatagramPacket(np, np.length, direccion, puerto);
				pool.enviar(dp);
				escribirLog("Mensaje con la cantidad de paquetes a enviar, enviado exitosamente !");
				
				
				int n;
				long sumaTam = 0;
				long ini = System.currentTimeMillis();
				// REVISAR SI SE CAMBIA LA CONDICION CON EL NUMERO DE PAQUETES O NO 
				escribirLog("Iniciando envío del archivo seleccionado . . .");
				while (sumaTam < archivoDeseado.length() && ( n = bis.read(mybytearray)) != 1) 
				{
					dp = new DatagramPacket(mybytearray, 0, mybytearray.length, direccion, puerto);
					pool.enviar(dp);
					hash.update(mybytearray, 0, n);
					sumaTam += n;
				}
				long fin = System.currentTimeMillis();
				bis.close();
				escribirLog("Archivo enviado completamente !");
				long tiempo = (fin - ini)/1000;
				escribirLog("Tiempo total de envío del archivo --> " + tiempo + " segundos");
				
				// Comprobación de la integridad con hashing
				escribirLog("Iniciando la comprobación de la integridad . . .");
				byte[] fileHashed = hash.digest();
				escribirLog("Hash calculado para el archivo enviado --> " + DatatypeConverter.printHexBinary(fileHashed));
				
				String hashRes = (FINARCH + SEPARADOR)+ DatatypeConverter.printHexBinary(fileHashed);
				b = hashRes.getBytes();
				dp = new DatagramPacket(b, b.length, direccion, puerto);
				pool.enviar(dp);
				escribirLog("Hash del archivo enviado correctamente al cliente !");
				
				System.out.println("send");

				//Recibe confirmación de recepción o error en el cliente
				// FORMA 1: ASUMIENDO QUE LE PAQUETE LE LLEGA A ESTE Y NO AL POOL
//				byte[] res = new byte[9];
//				dp = new DatagramPacket(res, res.length);
//				pool.recibir(dp);
//				String ans = new String(dp.getData(), 0, dp.getLength());
				
				// FORMA 2: ASUMIENTO QUE LE LLEGAN AL POOL. ESTO ASUME QUE LOS PUERTOS PARA CADA CLIENTE SON ÚNICOS.
				// ASUME QUE TENDRÁ UN NÚMERO DE CONEXIONES <= 25 al parecer de esta forma.
				while(estado.equals("")) {Thread.yield();}
				
//				if(ans.equalsIgnoreCase(RECIBIDO)) 
				if(estado.equalsIgnoreCase(RECIBIDO)) 
				{
					escribirLog("El cliente recibió el archivo CORRECTAMENTE e íntegramente :D !");
					escribirLog("Finalizando conexión éxitosamente ! . . .");
					fw.close();
				}
				else 
				{
					escribirLog("El cliente encontró un ERROR en la integridad del archivo :(");
					escribirLog("Finalizando conexión . . .");
					fw.close();
				}
			}
			else 
			{
				escribirLog("El archivo a enviar no existe :(");
				escribirLog("Finalizando conexión . . .");
				fw.close();
			}
		}
		catch (Exception e) {
			escribirLog("Error encontrado durante la ejecución: " + e.getMessage());
			escribirLog("Finalizando conexión . . .");
			fw.close();
			e.printStackTrace();
		} 
	}
	
	private void escribirLog(String mensaje) throws IOException {
		fw.write("[" + LocalTime.now()+"] [CLIENTE_" + id + "] " + mensaje + NEW_LINE );
	}
	
	public void setEstado(String nuevo) {
		this.estado = nuevo;
	}
}