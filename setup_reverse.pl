use File::Copy;
use Data::Dumper;
@files=glob("'/Library/Application Support/Perceptive Automation/Indigo 4/IndigoWebServer/templates/'*.html");

foreach $file (@files)
{
	copy($file,$file.".bak") or die;
	open INFILE,"<$file".".bak" or die;
	open OUTFILE,">$file" or die;
	while (<INFILE>)
	{
		s|href="/|href="./|;
		s|src="/|src="./|;   
		print OUTFILE $_ or die;
	}
	
}

exit;

