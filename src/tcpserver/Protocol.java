package tcpserver;

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
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalTime;
import javax.xml.bind.DatatypeConverter;
public class Protocol implements Runnable{

	public static final String PREPARADO = "PREPARADO";
	public static File folder  = new File("./data/");
	public static final String SEPARADOR = "$";
	public static final Integer TAMANIO_SEGMENTO = 8192;
	public static final String ERROR = "ERROR";
	public static final String ARCH = "ARCH";
	public static final String FINARCH = "FINARCH";
	public static final String RECIBIDO = "RECIBIDO";
	public static final String LLEGO = "LLEGO";
	public static final String NEW_LINE = "\n";
	private Socket sc;
	private boolean aceptaArchs;
	private int tiempoMuerte;
	private File archivo;
	private PoolThreads pool;

	public Protocol(Socket sc, boolean aceptaArchs, int tiempoMuerte, String archivo, PoolThreads pool) {
		this.sc = sc;
		this.aceptaArchs = aceptaArchs;
		this.tiempoMuerte = tiempoMuerte;
		this.archivo = new File(archivo);
		this.pool = pool;
	}

	public void run()
	{
		try 
		{
			sc.setSoTimeout(1000*tiempoMuerte);
			int i = 0;
			while(true)
			{
				if(aceptaArchs == true)
				{

					procesar(sc.getInputStream(), sc.getOutputStream(), sc.hashCode());
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

					//Duerme 5 segundos antes de validar si ya puede descargar el archivo
					//					this.aceptaArchs = pool.seAceptan(aceptaArchs);
				}
			}
		} 
		catch (Exception e) 
		{
			// TODO: handle exception
			e.printStackTrace();
		}
		finally
		{
			try 
			{
				if(aceptaArchs == true){
					pool.finSesionArchivos();
				}
				pool.finSesion();
				sc.close();
				//				pool.notify();
				pool.notificar();
			} 
			catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	/**
	 * Procesar el servicio
	 * @param leerDelCliente
	 * @param escribirleAlCliente
	 * @throws IOException 
	 */
	public void procesar(InputStream leerDelCliente , OutputStream escribirleAlCliente, int codigoUnico) throws IOException 
	{
		FileWriter fw = new FileWriter(new File("./data/logs/"+codigoUnico+".log" ));
		try 
		{
			BufferedReader bf = new BufferedReader(new InputStreamReader(leerDelCliente));
			PrintWriter pw = new PrintWriter(new OutputStreamWriter(escribirleAlCliente), true);
			String preparado = bf.readLine();
			if(preparado.equalsIgnoreCase(PREPARADO)) 
			{
				LocalTime ld = LocalTime.now();
				fw.write(ld.toString()+"NUEVO CLIENTE " + codigoUnico + NEW_LINE );
				File archivoDeseado = archivo;
				if(archivoDeseado != null) 
				{

					//avisamos el nombre del archivo se mandara 
					String header =  "NOMBRE" + SEPARADOR;
					ld = LocalTime.now();
					fw.write(ld.toString()+"CLIENTE " + codigoUnico + " ARCH " + archivoDeseado.getName() + NEW_LINE );
					header += archivoDeseado.getName();
					pw.println(header);

					byte[] mybytearray;
					BufferedInputStream bis;
					DataOutputStream dos;
					MessageDigest hash;


//					synchronized (archivo) 
//					{
					mybytearray = new byte[TAMANIO_SEGMENTO];
					bis = new BufferedInputStream(new FileInputStream(archivoDeseado));
					dos =  new DataOutputStream(escribirleAlCliente);
					hash = MessageDigest.getInstance("SHA-256");
					System.out.println("Longi " + archivoDeseado.length());
					dos.writeLong(archivoDeseado.length());
					dos.flush();
					
//					Thread.sleep(1000); // NO SE SI ESTO SE DEJARÍA CON EL CAMBIO, CREERÍA QUE NO
//					}

					int n ;
					long sumaTam = 0;
					while (sumaTam < archivoDeseado.length() && ( n = bis.read(mybytearray)) != 1) 
					{
						dos.write(mybytearray,0, n);
						hash.update(mybytearray, 0, n);
						dos.flush();
						bf.readLine().equalsIgnoreCase(LLEGO);
						sumaTam += n;
					}

					bis.close();
					ld = LocalTime.now();
					fw.write(ld.toString()+"CLIENTE " + codigoUnico + " ENVIADO ARCH " + archivoDeseado.getName() + NEW_LINE);
					//hashing
					byte[] fileHashed = hash.digest();

					String fin = (FINARCH + SEPARADOR)+ DatatypeConverter.printHexBinary(fileHashed);
					

					ld = LocalTime.now();
					fw.write(ld.toString()+"CLIENTE " + codigoUnico + " ENVIADO HASH DEL ARCH " + archivoDeseado.getName() + NEW_LINE);

					String rec = bf.readLine();
					//FINARCH$digest
					//escribirleAlCliente.write(outputStream.toByteArray());
					//					String finarch = DatatypeConverter.printHexBinary(outputStream.toByteArray());
					
					System.out.println("send");
					pw.println(fin);

					if(bf.readLine().equalsIgnoreCase(RECIBIDO)) 
					{
						ld = LocalTime.now();
						fw.write(ld.toString()+"CLIENTE " + codigoUnico + " FIN CONEXION " + archivoDeseado.getName() + NEW_LINE );
						fw.close();
						bf.close(); 
					}
					else 
					{
						ld = LocalTime.now();
						fw.write(ld.toString()+"CLIENTE " + codigoUnico + " NO LOGRO VERIFICAR INTEGRIDAD  " + archivoDeseado.getName() + NEW_LINE);
						fw.close();
					}
				}
				else 
				{
					ld = LocalTime.now();
					fw.write(ld.toString()+"CLIENTE " + codigoUnico + " ARCH INEXISTENTE " + NEW_LINE );
					fw.close();
				}

			}
			else {
				LocalTime ld = LocalTime.now();
				fw.write(ld.toString()+"CLIENTE " + codigoUnico + " NO SIGUE EL PROTOCOLO " + NEW_LINE);
				fw.close();
			}
		}
		catch (Exception e) {
			LocalTime ld = LocalTime.now();
			fw.write(ld.toString()+"CLIENTE " + codigoUnico + " ERROR " + e.getMessage() + NEW_LINE );
			fw.close();
			e.printStackTrace();
		} 
	}
}