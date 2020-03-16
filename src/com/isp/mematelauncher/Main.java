package com.isp.mematelauncher;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;


/**
 * Die Folgende Klasse ist der Launcher für das MeMate Projekt (https://github.com/isp-insoft-gmbh/MeMate).
 * Der Launcher sorgt dafür, dass die App immer auf dem neusten Stand ist.
 * Des weiteren wird noch einige openJDK JRE mit ausgeliefert, welche ebenfalls geupdatet werden kann.
 * 
 * @author nwe
 * @since 11.03.2020
 */

public class Main
{
  private final String       mainPath           =
      System.getenv( "APPDATA" ) + File.separator + "MeMate" + File.separator + "Installation" + File.separator;
  private final JProgressBar overallProgressBar = new JProgressBar( 0, 100 );
  private final JFrame       frame              = new JFrame();
  private final JLabel       progressLabeL      = new JLabel();
  private boolean            clientNeedsUpdate  = true;
  private boolean            jreNeedsUpdate     = true;
  private BufferedImage      background         = null;
  private URL                exeURL             = null;
  private URL                jreURL             = null;
  private URL                versionURL         = null;
  private String             jreFolderName      = null;

  public static void main( String[] args )
  {
    new Main().run();
  }

  public void run()
  {
    try
    {
      UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );
      background = ImageIO.read( getClass().getResource( "background.png" ) );
    }
    catch ( ClassNotFoundException | InstantiationException | IllegalAccessException
        | UnsupportedLookAndFeelException exception )
    {
      System.out.println( "Das Look and Feel konnte nicht gesetzt werden." );
      exception.printStackTrace();
    }
    catch ( IOException IOException )
    {
      System.out.println( "background.png konnte nicht gefunden werden." );
      IOException.printStackTrace();
    }
    String exeOutputPath = mainPath + "memate.exe";

    createDirectories();
    loadGUISettings();
    setURLS();

    checkForUpdates();
    if ( clientNeedsUpdate || !new File( exeOutputPath ).exists() )
    {
      downloadFile( exeURL, exeOutputPath );
    }
    overallProgressBar.setValue( 30 );
    if ( jreNeedsUpdate || !new File( mainPath + jreFolderName ).exists() )
    {
      downloadFile( jreURL, mainPath + "jre.zip" );
    }
    overallProgressBar.setValue( 90 );
    if ( jreNeedsUpdate || !new File( mainPath + jreFolderName ).exists() )
    {
      unzipJRE();
    }
    overallProgressBar.setValue( 95 );
    deleteZip();
    delteVersionInfo();
    progressLabeL.setText( "Done!" );
    System.out.println( "Done!" );
    overallProgressBar.setValue( 100 );
    try
    {
      frame.dispose();
      new ProcessBuilder( mainPath + "memate.exe" ).start();
    }
    catch ( IOException IOException )
    {
      System.out.println( "Die memate.exe konnte nicht gestartet werden." );
      IOException.printStackTrace();
    }
  }

  private void setURLS()
  {
    try
    {
      exeURL = new URL( "https://github.com/isp-insoft-gmbh/MeMate/releases/download/v0.9.9.2/memate.exe" );
      versionURL = new URL( "https://github.com/isp-insoft-gmbh/MeMate/releases/download/v0.9.9.2/version.properties" );
    }
    catch ( MalformedURLException exception )
    {
      System.out.println( "Die URL konnte nicht gefunden werden!" );
      exception.printStackTrace();
    }
  }


  private void createDirectories()
  {
    File meMateDir = new File( System.getenv( "APPDATA" ) + File.separator + "MeMate" );
    File installationDir = new File( System.getenv( "APPDATA" ) + File.separator + "MeMate" + File.separator + "Installation" );
    meMateDir.mkdir();
    installationDir.mkdir();
  }

  /**
   * Lädt zuerst die Datei version.properties aus dem GitHub Release herunter.
   * Nachfolgenden werden die Properties version, jreURL, jreSiganture und jreFolderName ausgelesen
   * und mit den Werten aus der lokalen installVersions.properties verglichen.
   * (Sollte es keine lokalen Properties geben so wird die version.properties zu installedVersions.properties
   * umbenannt und das Programm läuft weiter).
   */
  private void checkForUpdates()
  {
    System.out.println( "Checking for updates...." );
    progressLabeL.setText( "Checking for updates..." );
    String newestversion = null;
    String installedversion = null;
    String newsestJreURL = null;
    String newestJreSignature = null;
    String newestJreFolderName = null;
    String installedJreURL = null;
    String installedJreSignature = null;
    String installedJreFolderName = null;
    String propertiesOutputPath = mainPath + "version.properties";
    downloadFile( versionURL, propertiesOutputPath );
    try ( InputStream input = new FileInputStream( propertiesOutputPath ) )
    {
      Properties versionProperties = new Properties();
      versionProperties.load( input );
      newestversion = versionProperties.getProperty( "build_version" );
      newsestJreURL = versionProperties.getProperty( "jre_URL_Win64" );
      newestJreSignature = versionProperties.getProperty( "jre_SHA256_Signature_Win64" );
      newestJreFolderName = versionProperties.getProperty( "jre_FolderName_Win64" );
      jreURL = new URL( newsestJreURL );
      jreFolderName = newestJreFolderName;
    }
    catch ( Exception exception )
    {
      System.out.println( "Die version.properties konnten nicht geladen werden" );
      exception.printStackTrace();
    }

    File installedVersionsProperties = new File( mainPath + "installedVersions.properties" );
    if ( !installedVersionsProperties.exists() )
    {
      System.out.println( "Die installedVersions.properties konnten nicht gefunden werden" );
      File versionInfoProperties = new File( propertiesOutputPath );
      versionInfoProperties.renameTo( installedVersionsProperties );
    }
    else
    {
      try ( InputStream input = new FileInputStream( mainPath + "installedVersions.properties" ) )
      {
        Properties versionProperties = new Properties();
        versionProperties.load( input );
        installedversion = versionProperties.getProperty( "build_version" );
        installedJreURL = versionProperties.getProperty( "jre_URL_Win64" );
        installedJreSignature = versionProperties.getProperty( "jre_SHA256_Signature_Win64" );
        installedJreFolderName = versionProperties.getProperty( "jre_FolderName_Win64" );

      }
      catch ( Exception exception )
      {
        exception.printStackTrace();
      }
      System.out.println( "Installed client version: " + installedversion );
      System.out.println( "Newest client version: " + newestversion );
      System.out.println( "*************************************" );
      System.out.println( "Installed JRE URL: " + installedJreURL );
      System.out.println( "Newest JRE URL: " + newsestJreURL );
      System.out.println( "*************************************" );
      System.out.println( "Installed JRE Signature: " + installedJreSignature );
      System.out.println( "Newest JRE Signature: " + newestJreSignature );
      System.out.println( "*************************************" );
      System.out.println( "Installed JRE FolderName: " + installedJreFolderName );
      System.out.println( "Newest JRE FolderName: " + newestJreFolderName );
      System.out.println( "*************************************" );
      if ( installedversion.equals( newestversion ) )
      {
        clientNeedsUpdate = false;
        System.out.println( "Der Client ist auf dem neuesten Stand!" );
      }
      if ( installedJreURL.equals( newsestJreURL ) && installedJreSignature.equals( newestJreSignature )
          && installedJreFolderName.equals( newestJreFolderName ) )
      {
        jreNeedsUpdate = false;
        System.out.println( "Die JRE ist auf dem neuesten Stand!" );
      }
      if ( clientNeedsUpdate || jreNeedsUpdate )
      {
        File versionInfoProperties = new File( propertiesOutputPath );
        versionInfoProperties.renameTo( installedVersionsProperties );
      }
    }
    System.out.println( "*************************************" );
  }

  private void unzipJRE()
  {
    System.out.println( "Unzipping jre.zip..." );
    progressLabeL.setText( "Unzipping jre.zip..." );
    String jreZIP = mainPath + "jre.zip";
    File targetDirectory = new File( mainPath );
    var buffer = new byte[1024];
    try ( ZipInputStream zis = new ZipInputStream( new FileInputStream( jreZIP ) ) )
    {
      ZipEntry zipEntry = zis.getNextEntry();
      while ( zipEntry != null )
      {
        File nextFilePath = new File( targetDirectory.getAbsolutePath() + File.separator + zipEntry.getName() );
        if ( zipEntry.isDirectory() )
        {
          nextFilePath.mkdirs();
        }
        else
        {
          System.out.println( nextFilePath.getAbsolutePath() );
          nextFilePath.createNewFile();
          try ( FileOutputStream fos = new FileOutputStream( nextFilePath ) )
          {
            int len;
            while ( (len = zis.read( buffer )) > 0 )
            {
              fos.write( buffer, 0, len );
            }
          }
        }
        zipEntry = zis.getNextEntry();
      }
    }
    catch ( Exception e )
    {
      System.out.println( "Unzippen fehlgeschlagen!" );
      e.printStackTrace();
    }
    System.out.println( "*************************************" );
  }

  private void deleteZip()
  {
    System.out.println( "Deleting jre.zip..." );
    progressLabeL.setText( "Deleting jre.zip..." );
    File zip = new File( mainPath + "jre.zip" );
    try
    {
      zip.delete();
    }
    catch ( Exception e )
    {
      System.out.println( "Die jre.zip konnte nicht gefunden werden" );
    }
    System.out.println( "*************************************" );
  }

  private void delteVersionInfo()
  {
    System.out.println( "Deleting version.properties..." );
    progressLabeL.setText( "Deleting version.properties..." );
    File properties = new File( mainPath + "version.properties" );
    properties.delete();
    System.out.println( "*************************************" );
  }

  /*
   * Startet den Download einer Datei aus der angegebenen URl und speichert diese im outputPath.
   * Außerdem wird für jeden Download zuerst die Größe ausgegeben und der Downloadfortschritt 
   * wird sowohl auf der Konsole als auch auf der GUI dargestellt.
   */
  private void downloadFile( URL url, String outputPath )
  {
    try
    {
      if ( !outputPath.contains( "properties" ) )
      {
        progressLabeL.setText( "Downloading " + outputPath.replace( mainPath, "" ) + "...." );
      }
      URLConnection urlConnection = url.openConnection();
      urlConnection.connect();
      long fileSize = urlConnection.getContentLengthLong();
      long onePercent = fileSize / 100;
      ReadableByteChannel readableByteChannel = Channels.newChannel( url.openStream() );
      FileOutputStream fileOutputStream = new FileOutputStream( outputPath );
      FileChannel fileChannel = fileOutputStream.getChannel();
      TimerTask task = new TimerTask()
      {
        @Override
        public void run()
        {
          try
          {
            long percent = fileChannel.size() / onePercent;
            System.out.println( fileChannel.size() + " (" + percent + "%)" );
            switch ( outputPath.replace( mainPath, "" ) )
            {
              case "memate.exe" -> overallProgressBar.setValue( (int) (percent / 5) + 10 );
              case "jre.zip" -> overallProgressBar.setValue( (int) ((percent / 1.6665) + 30) );
              case "version.properties" -> overallProgressBar.setValue( (int) ((percent / 10)) );

              default -> throw new IllegalArgumentException( "Unexpected value: " + outputPath.replace( mainPath, "" ) );
            }
          }
          catch ( IOException e )
          {
            e.printStackTrace();
          }
        }
      };
      Timer timer = new Timer();
      System.out.println( "Starting download for " + outputPath.replace( mainPath, "" ) + " (" + fileSize + " Bytes)" );
      timer.schedule( task, 0, 20 );
      fileOutputStream.getChannel()
          .transferFrom( readableByteChannel, 0, Long.MAX_VALUE );
      timer.cancel();
      System.out.println( fileChannel.size() + "(100%)" );
      System.out.println( "*************************************" );
      fileOutputStream.close();
    }
    catch ( IOException e )
    {
      e.printStackTrace();
    }
  }

  private void loadGUISettings()
  {
    frame.setTitle( "MeMate Launcher" );
    frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
    progressLabeL.setForeground( new Color( 29, 205, 205 ) );
    progressLabeL.setHorizontalAlignment( JLabel.CENTER );
    progressLabeL.setFont( progressLabeL.getFont().deriveFont( 20f ) );
    overallProgressBar.setStringPainted( true );
    JPanel mainpanel = new JPanel()
    {
      @Override
      protected void paintComponent( Graphics g )
      {
        super.paintComponent( g );
        g.drawImage( background, 0, 0, this );
      }
    };
    mainpanel.setPreferredSize( new Dimension( background.getWidth(), background.getHeight() ) );
    mainpanel.setSize( new Dimension( background.getWidth(), background.getHeight() ) );
    frame.setContentPane( mainpanel );
    mainpanel.repaint();
    mainpanel.setLayout( new GridBagLayout() );

    GridBagConstraints upperFillLabel = new GridBagConstraints();
    upperFillLabel.gridx = 0;
    upperFillLabel.gridy = 0;
    upperFillLabel.gridwidth = 2;
    upperFillLabel.weighty = 1;
    upperFillLabel.fill = GridBagConstraints.BOTH;
    mainpanel.add( new JLabel(), upperFillLabel );
    GridBagConstraints leftFillLabel = new GridBagConstraints();
    leftFillLabel.gridx = 0;
    leftFillLabel.gridy = 1;
    leftFillLabel.gridheight = 2;
    leftFillLabel.weighty = 0.4;
    leftFillLabel.weightx = 1;
    leftFillLabel.fill = GridBagConstraints.HORIZONTAL;
    leftFillLabel.anchor = GridBagConstraints.WEST;
    mainpanel.add( new JLabel(), leftFillLabel );
    GridBagConstraints progressBarConstraints = new GridBagConstraints();
    progressBarConstraints.gridx = 1;
    progressBarConstraints.gridy = 1;
    progressBarConstraints.weighty = 0.2;
    progressBarConstraints.weightx = 0.8;
    progressBarConstraints.fill = GridBagConstraints.HORIZONTAL;
    progressBarConstraints.anchor = GridBagConstraints.WEST;
    progressBarConstraints.ipadx = 115;
    progressBarConstraints.insets = new Insets( 0, 0, 0, 60 );
    mainpanel.add( overallProgressBar, progressBarConstraints );
    GridBagConstraints progressLabeLConstraints = new GridBagConstraints();
    progressLabeLConstraints.gridx = 1;
    progressLabeLConstraints.gridy = 2;
    progressLabeLConstraints.weighty = 0;
    progressLabeLConstraints.weightx = 0.8;
    progressLabeLConstraints.fill = GridBagConstraints.HORIZONTAL;
    progressLabeLConstraints.anchor = GridBagConstraints.WEST;
    progressLabeLConstraints.insets = new Insets( 0, 0, 20, 70 );
    mainpanel.add( progressLabeL, progressLabeLConstraints );
    frame.pack();
    frame.setLocationRelativeTo( null );
    frame.setVisible( true );
    frame.setResizable( false );
  }
}
