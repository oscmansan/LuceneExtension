/* Represents a pair (term,weight) where
   term is a string and weight is a double
*/
public class TermWeight {

   private String text;
   
   private double weight;
   
   public TermWeight() {
       text = "";
       weight = 0;
   }
   
   public TermWeight(String t, double w) {
       text = t;
       weight = w;
   }

   
   public void setText(String t) {
       text = t;
   }
   
   public void setWeight(double w) {
       weight = w;
   }

   public String getText() {
       return text;
   }
   
   public double getWeight() {
        return weight;
   }
}
