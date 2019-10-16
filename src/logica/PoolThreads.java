package logica;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PoolThreads 
{
	public static final int PORT = 8080;
	
	private DatagramSocket ss;
	private static int nThreads;
	private static Integer nThreadsActivos = 0;
	private String archivo;
	private static Integer numeroSesiones=0;
	private static Boolean iniciaConcurrencia;
	private String[] hilos;
	private HashMap<String, Protocol> referencias;

	public String listarArchivos()
	{
		File directorio = new File("./data");
		String retorno = "";
		String[] nombres = directorio.list();
		for(int i = 0; i < nombres.length; i++)
		{
//			if(i == nombres.length-1)
			File f = new File(nombres[i]);
			if(!f.isDirectory())
			{
				retorno += (i+1) + ") " + nombres[i];
				
			}
			else
				retorno += (i+1) + ") " + nombres[i] + "\n";
		}
		return retorno;
	}

	public void seleccion(BufferedReader br) throws IOException
	{
		while(true){
			System.out.println("Digite el numero de un archivo");
			System.out.println(listarArchivos());
			File directorio = new File("./data");
			// System.out.println(directorio.listFiles().length);
			int arch = Integer.parseInt(br.readLine());
			if(arch < 1 || arch > directorio.listFiles().length)
			{
				System.out.println("Archivo incorrecto");
				continue;
			}
			archivo = directorio.listFiles()[arch-1].getPath();
			break;
		}
		while(true)
		{
			System.out.println("Ingrese el numero de clientes concurrentes a los que se les enviara el archivo");
			int num = Integer.parseInt(br.readLine());
			if(num < 0 || num > 25)
			{
				System.out.println("Solo puede tener entre 1 y 25 clientes concurrentes");
				continue;
			}
			nThreads = num;
			break;
		}
	}

	public void servidor() throws Exception
	{
		InputStreamReader isr = new InputStreamReader(System.in);
		BufferedReader br = new BufferedReader(isr);
		System.out.println("Empezando servidor maestro en puerto " + PORT);

		// Crea el socket que escucha en el puerto seleccionado.
		ss = new DatagramSocket(PORT);
		System.out.println("Socket creado.");

		seleccion(br);

		referencias = new HashMap<>();
		hilos = new String[nThreads];
		for(int i = 0; i < hilos.length; i++) { hilos[i] = ""; }
		ExecutorService executor= Executors.newFixedThreadPool(nThreads);
		System.out.println("Pool con "+nThreads+" threads ha sido creado.");
		System.out.println("Esperando solicitudes.");
		iniciaConcurrencia = false;

		while (true) {
			try 
			{ 
				if(numeroSesiones <= 25)
				{
					// Recibe el paquete de listo del cliente
					byte[] buf = new byte[9];
					DatagramPacket p = new DatagramPacket(buf, buf.length);
					ss.receive(p);
					String recibida = new String(p.getData(), 0, p.getLength());
					// Buscar el de todos que tiene el id o algo y hacer que se despierte del while o algo en el que se va a dejar
					Protocol s = referencias.get((p.getAddress().toString() + p.getPort()));
					if(s == null) {
						if(recibida.equals(Protocol.PREPARADO)) {
							boolean aceptaArchs = seAceptan(true);
							Protocol pro = new Protocol(aceptaArchs, archivo, this, p.getAddress(), p.getPort(), numeroSesiones);
							referencias.put((p.getAddress().toString() + p.getPort()), pro);
							executor.execute(pro);
							numeroSesiones++;						
						}
						else {
							throw new Exception("Algo ocurrió y llegó un paquete que no decía PREPARADO (ya existe una referencia al cliente de donde llegó)");
						}
					}
					else {						
						if(recibida.equals(Protocol.RECIBIDO) || recibida.equals(Protocol.ERROR)) {
							s.setEstado(recibida);
							referencias.remove((p.getAddress().toString() + p.getPort()));
						}
						else {
							throw new Exception("Algo ocurrió y llegó un paquete de RECIBIDO o ERROR a un delegado que aún no existe");
						}						
					}
				}
				else
				{
					wait();
				}
			} 
			catch (IOException e) {
				System.out.println("Error creando el socket cliente.");
				e.printStackTrace();
			}
		}
	}
	
	
	public boolean seAceptan(boolean primeraVez)
	{
		boolean aceptan = false;
		synchronized (nThreadsActivos) 
		{
			synchronized (iniciaConcurrencia) 
			{
				if(iniciaConcurrencia == false)
				{
					iniciaConcurrencia = true;
					//nThreadsActivos++;
					//aceptan = true;
				}
				else
				{
					if(primeraVez) 
					{
						
						if(nThreadsActivos < nThreads)
						{
							nThreadsActivos++;
						}
						
					}
					if(nThreads == nThreadsActivos && nThreads ==  numeroSesiones)
					{
						aceptan = true;
					}
					else if(numeroSesiones > nThreads ) 
					{
						aceptan = false;
					}
//					ESTO SOBRA SI ES AS� CON RETURN
//					else
//					{
//						aceptan = false;
//					}
				}
				return aceptan;
			}
		}
	}
	
	public boolean siguenBloqueadosLosArchivos()
	{
		return iniciaConcurrencia;
	}

	public void finSesionArchivos()
	{
		synchronized(nThreadsActivos)
		{
			synchronized (iniciaConcurrencia) 
			{
				nThreadsActivos--;
				if(nThreadsActivos == 0)
				{
					iniciaConcurrencia = false;
				}
			}
		}
	}

	public void finSesion()
	{
		synchronized (numeroSesiones) {
			numeroSesiones--;
		}
	}
	
	public synchronized void notificar() {
		if(numeroSesiones > 25) {
			notify();
		}
	}
	
	public void enviar(DatagramPacket paq) throws IOException {
		ss.send(paq);
	}
	
	public void recibir(DatagramPacket paq) throws IOException {
		ss.receive(paq);
	}

	public static void main(String ... args){
		try {
			PoolThreads pool = new PoolThreads();
			pool.servidor();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}