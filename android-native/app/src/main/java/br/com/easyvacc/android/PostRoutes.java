package br.com.easyvacc.android;

import java.util.Locale;

final class PostRoutes {
    static final class Point {
        final double lat,lon;
        Point(double lat,double lon){this.lat=lat;this.lon=lon;}
        String coordinates(){return String.format(Locale.ROOT,"%.7f,%.7f",lat,lon);}
    }
    static Point point(double lat,double lon){
        return Math.abs(lat)<=90&&Math.abs(lon)<=180?new Point(lat,lon):null;
    }
    static double distance(Point from,Point to){
        if(from==null||to==null)return Double.POSITIVE_INFINITY;
        double a=Math.sin(Math.toRadians(to.lat-from.lat)/2), b=Math.sin(Math.toRadians(to.lon-from.lon)/2);
        double h=a*a+Math.cos(Math.toRadians(from.lat))*Math.cos(Math.toRadians(to.lat))*b*b;
        return 12742*Math.asin(Math.sqrt(Math.max(0,Math.min(1,h))));
    }
    static Point unit(String name,String address){
        for(String[] item:SaquaremaDirectory.COORDINATES)
            if(Rules.postIdentity(item[0]).equals(Rules.postIdentity(name))&&Rules.postKey(item[1]).equals(Rules.postKey(address)))
                return point(Double.parseDouble(item[2]),Double.parseDouble(item[3]));
        return null;
    }
}
