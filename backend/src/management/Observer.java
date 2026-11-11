package management;

public interface Observer extends java.io.Serializable {
    void update(News news);
}